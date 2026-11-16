package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;
import java.util.List;

/** A day off cancelled these PUBLISHED periods; the teachers (main and assistant) are told once each. */
public record CalendarHolidayCancelled(
        String eventId,
        String tenantId,
        String holidayId,
        String holidayName,
        List<String> calendarEventIds,
        String actorId,
        OffsetDateTime occurredAt,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        String correlationId,
        String causationId
) {
    public CalendarHolidayCancelled {
        calendarEventIds = List.copyOf(calendarEventIds);
    }

    public CalendarHolidayCancelled(String eventId, String tenantId, String holidayId, String holidayName,
            List<String> calendarEventIds, String actorId, OffsetDateTime occurredAt, int schemaVersion) {
        this(eventId, tenantId, holidayId, holidayName, calendarEventIds, actorId, occurredAt, schemaVersion, holidayId,
                occurredAt.toInstant().toEpochMilli(), eventId, null);
    }

    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, aggregateId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
