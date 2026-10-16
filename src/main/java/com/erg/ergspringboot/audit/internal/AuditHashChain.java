package com.erg.ergspringboot.audit.internal;

import com.erg.ergspringboot.model.audit.AuditEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Deterministic SHA-256 chain over redacted immutable audit fields. */
@Component
public class AuditHashChain {
    private final ObjectMapper objectMapper;

    public AuditHashChain(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(AuditEvent event) {
        Map<String, Object> payload = new TreeMap<>();
        put(payload, "tenantId", event.getTenantId());
        put(payload, "actorId", event.getActorId());
        put(payload, "scopeType", event.getScopeType());
        put(payload, "scopeId", event.getScopeId());
        put(payload, "action", event.getAction());
        put(payload, "resourceType", event.getResourceType());
        put(payload, "resourceId", event.getResourceId());
        put(payload, "outcome", event.getOutcome() == null ? null : event.getOutcome().name());
        put(payload, "requestId", event.getRequestId());
        put(payload, "correlationId", event.getCorrelationId());
        put(payload, "causationId", event.getCausationId());
        put(payload, "details", canonicalValue(event.getDetails()));
        put(payload, "idempotencyKey", event.getIdempotencyKey());
        put(payload, "previousHash", event.getPreviousHash());
        put(payload, "occurredAt", instant(event.getOccurredAt()));
        put(payload, "retentionUntil", instant(event.getRetentionUntil()));
        put(payload, "redactionVersion", event.getRedactionVersion());
        return sha256(objectMapper.writeValueAsString(payload));
    }

    @SuppressWarnings("unchecked")
    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalValue(item)));
            return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(this::canonicalValue).toList();
        return value;
    }

    private String instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    private void put(Map<String, Object> target, String key, Object value) {
        target.put(key, value);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
