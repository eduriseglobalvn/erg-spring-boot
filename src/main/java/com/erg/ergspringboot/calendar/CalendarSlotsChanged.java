package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * PUBLISHED periods were moved, re-roomed or given to another teacher in one operation. {@code removed} lists who lost
 * a period (they are no longer on it, so the projection cannot find them); everyone still on it is read from the periods.
 */
public record CalendarSlotsChanged(
        String eventId,
        String tenantId,
        String batchId,
        String kind,
        List<String> calendarEventIds,
        List<RemovedPerson> removed,
        String actorId,
        OffsetDateTime occurredAt,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        String correlationId,
        String causationId
) {
    public CalendarSlotsChanged {
        calendarEventIds = List.copyOf(calendarEventIds);
        removed = List.copyOf(removed);
    }

    public CalendarSlotsChanged(String eventId, String tenantId, String batchId, String kind, List<String> calendarEventIds,
            List<RemovedPerson> removed, String actorId, OffsetDateTime occurredAt, int schemaVersion, long aggregateVersion) {
        this(eventId, tenantId, batchId, kind, calendarEventIds, removed, actorId, occurredAt, schemaVersion, batchId,
                aggregateVersion, eventId, null);
    }

    /** {@code role} is MAIN or ASSISTANT. */
    public record RemovedPerson(String calendarEventId, String userId, String name, String role) {}

    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, aggregateId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
