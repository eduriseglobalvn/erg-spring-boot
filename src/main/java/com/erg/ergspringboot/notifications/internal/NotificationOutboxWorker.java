package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.common.response.notifications.NotificationResponses.DeadLetterReplayResponse;
import com.erg.ergspringboot.common.response.notifications.NotificationResponses.OutboxRunResponse;
import com.erg.ergspringboot.notifications.persistence.NotificationDeliveryAttemptEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxEntity;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.delivery.NotificationDeliveryAdapterRegistry;
import com.erg.ergspringboot.notifications.delivery.NotificationDeliveryPayload;
import com.erg.ergspringboot.notifications.delivery.NotificationDeliveryRequest;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.notifications.push.SendResult;
import com.erg.ergspringboot.notifications.persistence.NotificationDeliveryAttemptRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationOutboxWorker {

    /** Unique per JVM so lease ownership checks distinguish instances (C15). */
    static final String WORKER_ID = workerId();

    /** Provider calls in flight at once (one pool shared by the scheduler and manual runs). */
    static final int PARALLELISM = 8;
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);

    /** One run stops sending after this; claimed rows it did not reach are released back to the queue. */
    Duration runBudget = Duration.ofSeconds(20);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationMessageRepository messageRepository;
    private final NotificationDeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationDeviceRegistrationRepository deviceRepository;
    private final NotificationDeliveryAdapterRegistry deliveryAdapters;
    private final NotificationPushProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final NotificationMetrics metrics;
    private final ThreadPoolExecutor deliveryPool = newDeliveryPool();

    public NotificationOutboxWorker(
            NotificationOutboxRepository outboxRepository,
            NotificationMessageRepository messageRepository,
            NotificationDeliveryAttemptRepository deliveryAttemptRepository,
            NotificationDeviceRegistrationRepository deviceRepository,
            NotificationDeliveryAdapterRegistry deliveryAdapters,
            NotificationPushProperties properties,
            PlatformTransactionManager transactionManager,
            NotificationMetrics metrics
    ) {
        this.outboxRepository = outboxRepository;
        this.messageRepository = messageRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deviceRepository = deviceRepository;
        this.deliveryAdapters = deliveryAdapters;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${erg.notifications.push.worker.fixed-delay-ms:10000}")
    public void scheduledRun() {
        if (properties.isEnabled() && properties.getWorker().isEnabled()) {
            runDueBatch();
        }
    }

    public OutboxRunResponse runDueBatch() {
        return runDueBatch(null);
    }

    public OutboxRunResponse runDueBatch(String tenantId) {
        return runDueBatch(tenantId, Integer.MAX_VALUE);
    }

    /** {@code maxRows} caps the claim, so a manual run from an HTTP request never pins the thread for a whole poll. */
    public OutboxRunResponse runDueBatch(String tenantId, int maxRows) {
        TenantContext previous = TenantContext.current();
        if ((tenantId == null || tenantId.isBlank()) && previous == null) {
            TenantContext.set(TenantContext.maintenance());
        }
        try {
            List<NotificationOutboxEntity> due = claimDueBatch(tenantId, Math.max(1, maxRows));
            long deadline = System.nanoTime() + runBudget.toNanos();
            Map<String, NotificationMessageEntity> messages = loadMessages(due);
            TenantContext.Carrier tenant = TenantContext.capture();
            List<String> unprocessed = new CopyOnWriteArrayList<>();
            List<Future<DispatchStatus>> pending = new ArrayList<>(due.size());
            for (NotificationOutboxEntity outbox : due) {
                try {
                    pending.add(deliveryPool.submit(() -> deliver(outbox, messages.get(outbox.getNotificationId()), deadline, tenant, unprocessed)));
                } catch (RejectedExecutionException shuttingDown) {
                    unprocessed.add(outbox.getId());
                }
            }
            int sent = 0;
            int failed = 0;
            int retried = 0;
            int dead = 0;
            for (Future<DispatchStatus> future : pending) {
                DispatchStatus status = await(future);
                if (status == DispatchStatus.SENT) {
                    sent += 1;
                } else if (status == DispatchStatus.FAILED) {
                    failed += 1;
                } else if (status == DispatchStatus.RETRIED) {
                    retried += 1;
                } else if (status == DispatchStatus.DEAD) {
                    dead += 1;
                }
            }
            releaseUnprocessed(unprocessed);
            return new OutboxRunResponse(due.size(), sent, failed, retried, dead);
        } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.set(previous);
        }
    }

    /** One row on a pool thread: the lease renewal, the provider call and the atomic result transaction. */
    private DispatchStatus deliver(NotificationOutboxEntity outbox, NotificationMessageEntity message, long deadline,
            TenantContext.Carrier tenant, List<String> unprocessed) {
        if (System.nanoTime() - deadline >= 0) {
            unprocessed.add(outbox.getId());
            return null;
        }
        try (TenantContext.Scope ignored = TenantContext.install(tenant)) {
            // Extend the lease per item: a slow provider must not let another instance reclaim and
            // double-send rows that are still queued in this batch.
            if (outboxRepository.renewLease(outbox.getId(), WORKER_ID, OffsetDateTime.now()) == 0) {
                return null;
            }
            return recordResult(outbox.getId(), send(outbox, message));
        } catch (RuntimeException failure) {
            // The row keeps its lease and is reclaimed when it expires; the other rows of the batch go on.
            log.warn("Outbox row {} failed unexpectedly: {}", outbox.getId(), failure.getClass().getSimpleName());
            return null;
        }
    }

    private static DispatchStatus await(Future<DispatchStatus> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException failure) {
            return null;
        }
    }

    private Map<String, NotificationMessageEntity> loadMessages(List<NotificationOutboxEntity> due) {
        Set<String> ids = new HashSet<>();
        due.forEach(outbox -> ids.add(outbox.getNotificationId()));
        Map<String, NotificationMessageEntity> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            messageRepository.findAllById(ids).forEach(message -> byId.put(message.getId(), message));
        }
        return byId;
    }

    /** Rows claimed but not sent (budget spent) go straight back to the queue instead of waiting out the lease. */
    private void releaseUnprocessed(List<String> ids) {
        if (!ids.isEmpty()) {
            outboxRepository.releaseLease(ids, WORKER_ID);
        }
    }

    @PreDestroy
    void shutdown() {
        deliveryPool.shutdown();
    }

    /**
     * Return dead-lettered notifications to the retry queue so an operator can replay deliveries that
     * exhausted their attempts (e.g. after a provider outage). Bounded and tenant-scoped: it resets
     * attempt_count so the normal retry/backoff state machine takes over again, and clears any lease.
     */
    public DeadLetterReplayResponse replayDeadLetters(String tenantId, int limit) {
        if (tenantId == null || tenantId.isBlank()) {
            return new DeadLetterReplayResponse(0);
        }
        int bounded = Math.max(1, Math.min(500, limit));
        Integer replayed = transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            List<NotificationOutboxEntity> dead =
                    outboxRepository.findDeadForUpdateByTenant(tenantId, bounded);
            for (NotificationOutboxEntity outbox : dead) {
                outbox.setStatus("retry");
                outbox.setAttemptCount(0);
                outbox.setNextAttemptAt(now);
                outbox.setLockedAt(null);
                outbox.setLockedBy(null);
                outboxRepository.save(outbox);
            }
            return dead.size();
        });
        return new DeadLetterReplayResponse(replayed == null ? 0 : replayed);
    }

    private List<NotificationOutboxEntity> claimDueBatch(String tenantId, int maxRows) {
        return transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime staleBefore = now.minus(properties.getWorker().getLeaseTimeout());
            int pollSize = Math.max(1, Math.min(Math.min(500, maxRows), properties.getWorker().getPollSize()));
            List<NotificationOutboxEntity> due = tenantId == null || tenantId.isBlank()
                    ? outboxRepository.findDueForUpdate(now, staleBefore, pollSize)
                    : outboxRepository.findDueForUpdateByTenant(tenantId, now, staleBefore, pollSize);
            for (NotificationOutboxEntity outbox : due) {
                outbox.setStatus("processing");
                outbox.setLockedAt(now);
                outbox.setLockedBy(WORKER_ID);
                outboxRepository.save(outbox);
            }
            return List.copyOf(due);
        });
    }

    private DispatchStatus recordResult(String outboxId, SendResult result) {
        // Record only while the lease is still ours; otherwise another instance owns the row now.
        return transactionTemplate.execute(status -> outboxRepository.findLeasedForUpdate(outboxId, WORKER_ID)
                .map(outbox -> applyResult(outbox, result))
                .orElse(DispatchStatus.LEASE_LOST));
    }

    private static ThreadPoolExecutor newDeliveryPool() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(PARALLELISM, PARALLELISM, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), Thread.ofPlatform().daemon().name("notification-outbox-", 0).factory());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private static String workerId() {
        String host;
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException | SecurityException exception) {
            host = "unknown-host";
        }
        // notification_outbox.locked_by is varchar(80): keep the UUID, trim the host.
        String safeHost = host.length() <= 43 ? host : host.substring(0, 43);
        return safeHost + ":" + java.util.UUID.randomUUID();
    }

    private DispatchStatus applyResult(NotificationOutboxEntity outbox, SendResult result) {
        recordAttempt(outbox, result);
        metrics.sendResult(outbox.getTargetKind(), result.errorCode(), result.success(), result.durationMs());
        int attemptCount = nullToZero(outbox.getAttemptCount()) + 1;
        outbox.setAttemptCount(attemptCount);
        outbox.setLockedAt(OffsetDateTime.now());
        outbox.setLockedBy(WORKER_ID);
        outbox.setLastErrorCode(result.errorCode());
        outbox.setLastErrorMessage(sample(result.errorMessage()));
        DispatchStatus dispatchStatus;
        if (result.success()) {
            outbox.setStatus("sent");
            dispatchStatus = DispatchStatus.SENT;
        } else if (result.invalidTarget()) {
            outbox.setStatus("failed");
            disableInvalidDevice(outbox);
            dispatchStatus = DispatchStatus.FAILED;
        } else if (result.retryable() && attemptCount < outbox.getMaxAttempts()) {
            outbox.setStatus("retry");
            outbox.setNextAttemptAt(OffsetDateTime.now().plus(backoff(attemptCount, result.retryAfter())));
            dispatchStatus = DispatchStatus.RETRIED;
        } else {
            outbox.setStatus("dead");
            dispatchStatus = DispatchStatus.DEAD;
        }
        outboxRepository.save(outbox);
        return dispatchStatus;
    }

    private SendResult send(NotificationOutboxEntity outbox, NotificationMessageEntity message) {
        if (message == null) {
            return SendResult.failure(false, false, 0, "NOTIFICATION_MISSING", "Notification message is missing.", null, 0);
        }
        NotificationDeliveryPayload payload = new NotificationDeliveryPayload(
                message.getTitle(),
                message.getBodyPreview(),
                message.getActionUrl(),
                message.getCollapseKey(),
                outbox.getPayload() == null ? Map.of() : outbox.getPayload()
        );
        return deliveryAdapters.deliver(new NotificationDeliveryRequest(
                outbox.getTenantId(),
                outbox.getNotificationId(),
                firstText(message.getChannel(), "push"),
                firstText(outbox.getTargetKind(), "unknown"),
                firstText(outbox.getProvider(), "firebase"),
                outbox.getTargetRef(),
                payload
        ));
    }

    private void recordAttempt(NotificationOutboxEntity outbox, SendResult result) {
        NotificationDeliveryAttemptEntity attempt = new NotificationDeliveryAttemptEntity();
        attempt.setTenantId(outbox.getTenantId());
        attempt.setOutboxId(outbox.getId());
        attempt.setNotificationId(outbox.getNotificationId());
        attempt.setDeviceRegistrationId(outbox.getPayload() == null ? null : outbox.getPayload().get("deviceRegistrationId"));
        attempt.setTargetKind(outbox.getTargetKind());
        attempt.setTargetRefHash(sha256(outbox.getTargetRef()));
        attempt.setProvider(firstText(outbox.getProvider(), "firebase"));
        attempt.setProviderMessageId(result.providerMessageId());
        attempt.setHttpStatus(result.httpStatus() == 0 ? null : result.httpStatus());
        attempt.setFirebaseErrorCode(result.errorCode());
        attempt.setProviderErrorCode(result.errorCode());
        attempt.setProviderErrorMessage(sample(result.errorMessage()));
        attempt.setStatus(result.success() ? "sent" : "failed");
        attempt.setDurationMs(result.durationMs());
        attempt.setResponseSample(sample(result.errorMessage()));
        deliveryAttemptRepository.save(attempt);
    }

    private void disableInvalidDevice(NotificationOutboxEntity outbox) {
        String deviceId = outbox.getPayload() == null ? null : outbox.getPayload().get("deviceRegistrationId");
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        deviceRepository.findById(deviceId)
                .filter(device -> outbox.getTenantId().equals(device.getTenantId()))
                .ifPresent(this::disable);
    }

    private void disable(NotificationDeviceRegistrationEntity device) {
        device.setEnabled(false);
        device.setRevokedAt(OffsetDateTime.now());
        deviceRepository.save(device);
    }

    private Duration backoff(int attempt, Duration retryAfter) {
        if (retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero()) {
            return retryAfter;
        }
        long baseSeconds = Math.max(1, properties.getWorker().getInitialBackoff().toSeconds());
        long maxSeconds = Math.max(baseSeconds, properties.getWorker().getMaxBackoff().toSeconds());
        long computed = baseSeconds * (1L << Math.min(6, Math.max(0, attempt - 1)));
        long capped = Math.min(maxSeconds, computed);
        long jitterBound = Math.max(1, capped / 5);
        return Duration.ofSeconds(capped + ThreadLocalRandom.current().nextLong(jitterBound + 1));
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String sample(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private String firstText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private enum DispatchStatus {
        SENT,
        FAILED,
        RETRIED,
        DEAD,
        LEASE_LOST
    }
}
