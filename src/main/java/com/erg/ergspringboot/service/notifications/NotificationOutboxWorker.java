package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.common.response.notifications.NotificationResponses.OutboxRunResponse;
import com.erg.ergspringboot.model.notifications.NotificationDeliveryAttemptEntity;
import com.erg.ergspringboot.model.notifications.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.model.notifications.NotificationMessageEntity;
import com.erg.ergspringboot.model.notifications.NotificationOutboxEntity;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.notifications.push.FirebasePushPayload;
import com.erg.ergspringboot.notifications.push.FirebasePushSender;
import com.erg.ergspringboot.notifications.push.SendResult;
import com.erg.ergspringboot.repository.notifications.NotificationDeliveryAttemptRepository;
import com.erg.ergspringboot.repository.notifications.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.repository.notifications.NotificationMessageRepository;
import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationOutboxWorker {

    private static final String WORKER_ID = "erg-spring";

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationMessageRepository messageRepository;
    private final NotificationDeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationDeviceRegistrationRepository deviceRepository;
    private final FirebasePushSender pushSender;
    private final NotificationPushProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final NotificationTokenCipher tokenCipher;
    private final NotificationMetrics metrics;

    public NotificationOutboxWorker(
            NotificationOutboxRepository outboxRepository,
            NotificationMessageRepository messageRepository,
            NotificationDeliveryAttemptRepository deliveryAttemptRepository,
            NotificationDeviceRegistrationRepository deviceRepository,
            FirebasePushSender pushSender,
            NotificationPushProperties properties,
            PlatformTransactionManager transactionManager,
            NotificationTokenCipher tokenCipher,
            NotificationMetrics metrics
    ) {
        this.outboxRepository = outboxRepository;
        this.messageRepository = messageRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deviceRepository = deviceRepository;
        this.pushSender = pushSender;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.tokenCipher = tokenCipher;
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
        TenantContext previous = TenantContext.current();
        if ((tenantId == null || tenantId.isBlank()) && previous == null) {
            TenantContext.set(TenantContext.maintenance());
        }
        try {
        List<NotificationOutboxEntity> due = claimDueBatch(tenantId);
        int sent = 0;
        int failed = 0;
        int retried = 0;
        int dead = 0;
        for (NotificationOutboxEntity outbox : due) {
            SendResult result = send(outbox);
            DispatchStatus status = recordResult(outbox.getId(), result);
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
        return new OutboxRunResponse(due.size(), sent, failed, retried, dead);
        } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.set(previous);
        }
    }

    private List<NotificationOutboxEntity> claimDueBatch(String tenantId) {
        return transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime staleBefore = now.minus(properties.getWorker().getLeaseTimeout());
            int pollSize = Math.max(1, Math.min(500, properties.getWorker().getPollSize()));
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
        return transactionTemplate.execute(status -> outboxRepository.findById(outboxId)
                .map(outbox -> applyResult(outbox, result))
                .orElse(DispatchStatus.DEAD));
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

    private SendResult send(NotificationOutboxEntity outbox) {
        NotificationMessageEntity message = messageRepository.findById(outbox.getNotificationId()).orElse(null);
        if (message == null) {
            return SendResult.failure(false, false, 0, "NOTIFICATION_MISSING", "Notification message is missing.", null, 0);
        }
        FirebasePushPayload payload = new FirebasePushPayload(
                message.getTitle(),
                message.getBodyPreview(),
                message.getActionUrl(),
                message.getCollapseKey(),
                outbox.getPayload() == null ? Map.of() : outbox.getPayload()
        );
        try {
            return switch (outbox.getTargetKind()) {
                case "registration_token" -> pushSender.sendToRegistrationToken(payload, tokenCipher.decrypt(outbox.getTargetRef()));
                case "fid" -> pushSender.sendToFid(payload, outbox.getTargetRef());
                case "topic" -> pushSender.sendToTopic(payload, outbox.getTargetRef());
                default -> SendResult.failure(false, false, 0, "UNKNOWN_TARGET_KIND", "Unsupported notification target kind.", null, 0);
            };
        } catch (RuntimeException exception) {
            return SendResult.failure(false, false, 0, "TARGET_DECRYPT_FAILED", exception.getMessage(), null, 0);
        }
    }

    private void recordAttempt(NotificationOutboxEntity outbox, SendResult result) {
        NotificationDeliveryAttemptEntity attempt = new NotificationDeliveryAttemptEntity();
        attempt.setTenantId(outbox.getTenantId());
        attempt.setOutboxId(outbox.getId());
        attempt.setNotificationId(outbox.getNotificationId());
        attempt.setDeviceRegistrationId(outbox.getPayload() == null ? null : outbox.getPayload().get("deviceRegistrationId"));
        attempt.setTargetKind(outbox.getTargetKind());
        attempt.setTargetRefHash(sha256(outbox.getTargetRef()));
        attempt.setProvider("firebase");
        attempt.setProviderMessageId(result.providerMessageId());
        attempt.setHttpStatus(result.httpStatus() == 0 ? null : result.httpStatus());
        attempt.setFirebaseErrorCode(result.errorCode());
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
        DEAD
    }
}
