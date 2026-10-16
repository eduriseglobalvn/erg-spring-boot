package com.erg.ergspringboot.audit;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Framework-neutral append request used by module command boundaries. */
public record AuditRecord(
        String tenantId,
        String actorId,
        String scopeType,
        String scopeId,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        String requestId,
        String correlationId,
        String causationId,
        Map<String, Object> details,
        String idempotencyKey,
        OffsetDateTime occurredAt,
        OffsetDateTime retentionUntil
) {
    public AuditRecord {
        tenantId = required(tenantId, "tenantId");
        action = required(action, "action");
        resourceType = required(resourceType, "resourceType");
        outcome = outcome == null ? AuditOutcome.SUCCESS : outcome;
        details = details == null ? Map.of() : new LinkedHashMap<>(details);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
