package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

/** A teaching-schedule batch was cancelled; {@code cancelledEvents} future periods were cancelled with it. */
public record CalendarBatchCancelled(
        String eventId,
        String tenantId,
        String batchId,
        String actorId,
        OffsetDateTime occurredAt,
        int schemaVersion,
        int cancelledEvents,
        String aggregateId,
        long aggregateVersion,
        String correlationId,
        String causationId
) {
    public CalendarBatchCancelled(String eventId, String tenantId, String batchId, String actorId,
            OffsetDateTime occurredAt, int schemaVersion, int cancelledEvents, long aggregateVersion) {
        this(eventId, tenantId, batchId, actorId, occurredAt, schemaVersion, cancelledEvents, batchId,
                aggregateVersion, eventId, null);
    }

    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, aggregateId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
