package com.erg.ergspringboot.auth.internal.mail;

import com.erg.ergspringboot.service.auth.AccountMailSender;
import com.erg.ergspringboot.model.outbox.OutboxEvent;
import com.erg.ergspringboot.repository.outbox.OutboxEventRepository;
import com.erg.ergspringboot.auth.internal.outbox.OutboxPayloadCipher;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Writes mail intent in the caller transaction; SMTP is performed by the outbox worker. */
@Primary
@Service
public class TransactionalAccountMailSender implements AccountMailSender {
    private static final String INVITE = "ACCOUNT_TEACHER_INVITE";
    private static final String RECOVERY = "ACCOUNT_RECOVERY_CODE";
    /** Payload key read by TransactionalOutboxWorker; legacy rows still carry a plain "recipient". */
    public static final String RECIPIENT_CIPHERTEXT = "recipientCiphertext";
    private final OutboxEventRepository repository;
    private final OutboxPayloadCipher cipher;

    public TransactionalAccountMailSender(OutboxEventRepository repository, OutboxPayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Override
    public void sendTeacherInvite(String recipient, String fullName, String temporaryCredential) {
        enqueue(INVITE, digest(recipient) + ":" + digest(temporaryCredential), payload(recipient, fullName, temporaryCredential));
    }

    @Override
    public void sendRecoveryEmailCode(String recipient, String fullName, String code) {
        enqueue(RECOVERY, digest(recipient) + ":" + digest(code), payload(recipient, fullName, code));
    }

    private void enqueue(String eventType, String idempotencyKey, Map<String, Object> payload) {
        String tenant = com.erg.ergspringboot.platform.security.TenantContext.current() == null
                ? "erg" : com.erg.ergspringboot.platform.security.TenantContext.current().tenantId();
        if (repository.findByTenantIdAndIdempotencyKey(tenant, idempotencyKey).isPresent()) return;
        OutboxEvent event = new OutboxEvent();
        event.setId(IdGenerator.newId());
        event.setTenantId(tenant);
        event.setEventType(eventType);
        event.setIdempotencyKey(idempotencyKey);
        event.setPayload(payload);
        event.setAvailableAt(OffsetDateTime.now());
        event.setCreatedAt(OffsetDateTime.now());
        repository.save(event);
    }

    /** Recipient address and one-time secret are stored encrypted; neither appears in the idempotency key. */
    private Map<String, Object> payload(String recipient, String fullName, String secret) {
        return Map.of(RECIPIENT_CIPHERTEXT, cipher.encrypt(recipient), "fullName", safe(fullName),
                "secret", cipher.encrypt(secret));
    }

    private String safe(String value) { return value == null ? "" : value; }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
