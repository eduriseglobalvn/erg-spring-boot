package com.erg.ergspringboot.audit;

import java.time.OffsetDateTime;
import java.util.Map;

/** Persistence-free response shape for audit readers and exporters. */
public record AuditEntry(
        String id,
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
        String eventHash,
        OffsetDateTime occurredAt,
        OffsetDateTime retentionUntil
) {
}
