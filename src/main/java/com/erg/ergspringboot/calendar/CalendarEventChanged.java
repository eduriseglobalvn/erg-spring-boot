package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

/** A PUBLISHED period was edited (room or note); the version makes each edit a distinct event for consumers. */
public record CalendarEventChanged(
        String eventId,
        String tenantId,
        String calendarEventId,
        String batchId,
        String actorId,
        OffsetDateTime occurredAt,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        String correlationId,
        String causationId
) {
    public CalendarEventChanged(String eventId, String tenantId, String calendarEventId, String batchId,
            String actorId, OffsetDateTime occurredAt, int schemaVersion, long aggregateVersion) {
        this(eventId, tenantId, calendarEventId, batchId, actorId, occurredAt, schemaVersion, calendarEventId,
                aggregateVersion, eventId, null);
    }

    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, aggregateId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
