package com.erg.ergspringboot.platform.events;

import java.time.OffsetDateTime;

/** Required envelope metadata for durable business events. */
public record ModuleEventMetadata(
        String eventId,
        int eventVersion,
        String tenantId,
        String actorId,
        String aggregateId,
        long aggregateVersion,
        OffsetDateTime occurredAt,
        String correlationId,
        String causationId
) {
    public ModuleEventMetadata {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        if (eventVersion < 1) throw new IllegalArgumentException("eventVersion must be positive");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (aggregateId == null || aggregateId.isBlank()) throw new IllegalArgumentException("aggregateId is required");
        if (aggregateVersion < 0) throw new IllegalArgumentException("aggregateVersion must not be negative");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
        if (correlationId == null || correlationId.isBlank()) correlationId = eventId;
    }
}
