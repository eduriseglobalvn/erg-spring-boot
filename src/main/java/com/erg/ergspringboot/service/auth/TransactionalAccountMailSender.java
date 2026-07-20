package com.erg.ergspringboot.service.auth;

import com.erg.ergspringboot.model.outbox.OutboxEvent;
import com.erg.ergspringboot.repository.outbox.OutboxEventRepository;
import com.erg.ergspringboot.service.outbox.OutboxPayloadCipher;
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
    private final OutboxEventRepository repository;
    private final OutboxPayloadCipher cipher;

    public TransactionalAccountMailSender(OutboxEventRepository repository, OutboxPayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Override
    public void sendTeacherInvite(String recipient, String fullName, String temporaryCredential) {
        enqueue(INVITE, recipient + ":" + digest(temporaryCredential),
                Map.of("recipient", recipient, "fullName", safe(fullName), "secret", cipher.encrypt(temporaryCredential)));
    }

    @Override
    public void sendRecoveryEmailCode(String recipient, String fullName, String code) {
        enqueue(RECOVERY, recipient + ":" + digest(code),
                Map.of("recipient", recipient, "fullName", safe(fullName), "secret", cipher.encrypt(code)));
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
