package com.erg.ergspringboot.audit;

import java.time.OffsetDateTime;

/** Public Modulith event for post-commit audit projections and replay. */
public record AuditRecorded(
        String eventId,
        int eventVersion,
        String tenantId,
        String actorId,
        String action,
        String resourceType,
        String resourceId,
        String eventHash,
        String correlationId,
        String causationId,
        OffsetDateTime occurredAt
) {
    public AuditRecorded {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        if (eventVersion < 1) throw new IllegalArgumentException("eventVersion must be positive");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action is required");
        if (resourceType == null || resourceType.isBlank()) throw new IllegalArgumentException("resourceType is required");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
    }
}
