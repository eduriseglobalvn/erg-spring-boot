package com.erg.ergspringboot.service.outbox;

import com.erg.ergspringboot.model.outbox.OutboxEvent;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.repository.outbox.OutboxEventRepository;
import com.erg.ergspringboot.service.auth.SmtpAccountMailSender;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded, lease-based dispatcher for durable external side effects. */
@Service
@ConditionalOnProperty(prefix = "erg.outbox.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransactionalOutboxWorker {
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 8;
    private static final Duration LEASE = Duration.ofSeconds(60);
    /** Per-instance lease owner (host:UUID), stored in outbox_events.locked_by (V133). */
    static final String WORKER_ID = workerId();
    private final OutboxEventRepository repository;
    private final SmtpAccountMailSender smtp;
    private final OutboxPayloadCipher cipher;
    private final TransactionTemplate transaction;

    public TransactionalOutboxWorker(
            OutboxEventRepository repository,
            SmtpAccountMailSender smtp,
            OutboxPayloadCipher cipher,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.smtp = smtp;
        this.cipher = cipher;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${erg.outbox.worker.fixed-delay-ms:5000}")
    public void scheduledRun() { runDueBatch(); }

    public int runDueBatch() {
        List<OutboxEvent> due;
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            due = transaction.execute(status -> {
                OffsetDateTime now = OffsetDateTime.now();
                List<OutboxEvent> events = repository.findDueForUpdate(now, BATCH_SIZE);
                OffsetDateTime lease = now.plus(LEASE);
                events.forEach(event -> {
                    event.setStatus("processing");
                    event.setLeaseUntil(lease);
                });
                if (!events.isEmpty()) {
                    repository.saveAll(events);
                    repository.flush();
                    repository.markLockedBy(events.stream().map(OutboxEvent::getId).toList(), WORKER_ID);
                }
                return events;
            });
        }
        if (due == null) return 0;
        for (OutboxEvent event : due) {
            if (renewLease(event)) dispatch(event);
        }
        return due.size();
    }

    /** Re-arms the lease per item so a slow batch is never reclaimed and re-sent by another instance (C15). */
    private boolean renewLease(OutboxEvent event) {
        try (TenantContext.Scope ignored = TenantContext.scope(
                TenantContext.of(event.getTenantId(), null, "system"))) {
            Integer renewed = transaction.execute(status ->
                    repository.renewLease(event.getId(), WORKER_ID, OffsetDateTime.now().plus(LEASE)));
            return renewed != null && renewed > 0;
        }
    }

    private static String workerId() {
        String host;
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException | SecurityException exception) {
            host = "unknown-host";
        }
        return (host.length() <= 59 ? host : host.substring(0, 59)) + ":" + java.util.UUID.randomUUID();
    }

    private void dispatch(OutboxEvent event) {
        try (TenantContext.Scope ignored = TenantContext.scope(
                TenantContext.of(event.getTenantId(), null, "system"))) {
            Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
            String encryptedRecipient = string(payload, "recipientCiphertext");
            String recipient = encryptedRecipient.isEmpty() ? string(payload, "recipient") : cipher.decrypt(encryptedRecipient);
            String fullName = string(payload, "fullName");
            if ("ACCOUNT_TEACHER_INVITE".equals(event.getEventType())) {
                smtp.sendTeacherInvite(recipient, fullName, cipher.decrypt(string(payload, "secret")));
            } else if ("ACCOUNT_RECOVERY_CODE".equals(event.getEventType())) {
                smtp.sendRecoveryEmailCode(recipient, fullName, cipher.decrypt(string(payload, "secret")));
            } else {
                throw new IllegalArgumentException("unsupported outbox event type: " + event.getEventType());
            }
            transition(event.getId(), true, null);
        } catch (RuntimeException failure) {
            // Provider messages often echo the recipient address; persist only the failure type.
            transition(event.getId(), false, failure.getClass().getSimpleName());
        }
    }

    private void transition(String id, boolean success, String error) {
        // Only the current lease owner may record the outcome; a lost lease means another instance owns it.
        transaction.executeWithoutResult(status -> repository.findLeasedForUpdate(id, WORKER_ID).ifPresent(event -> {
            event.setAttempts(event.getAttempts() + 1);
            event.setLeaseUntil(null);
            event.setLastError(error == null ? null : error.substring(0, Math.min(1000, error.length())));
            if (success) {
                event.setStatus("sent");
                event.setCompletedAt(OffsetDateTime.now());
            } else if (event.getAttempts() >= MAX_ATTEMPTS) {
                event.setStatus("dead");
            } else {
                event.setStatus("retry");
                event.setAvailableAt(OffsetDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(8, event.getAttempts()))));
            }
            repository.save(event);
        }));
    }

    private String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
