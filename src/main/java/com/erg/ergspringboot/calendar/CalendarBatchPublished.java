package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

/** Small, immutable module event. Delivery is at-least-once. */
public record CalendarBatchPublished(
        String eventId,
        String tenantId,
        String batchId,
        String actorId,
        OffsetDateTime occurredAt,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        String correlationId,
        String causationId
) {
    public CalendarBatchPublished(String eventId, String tenantId, String batchId, String actorId,
            OffsetDateTime occurredAt, int schemaVersion) {
        this(eventId, tenantId, batchId, actorId, occurredAt, schemaVersion,
                batchId, 0L, eventId, null);
    }

    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, aggregateId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
