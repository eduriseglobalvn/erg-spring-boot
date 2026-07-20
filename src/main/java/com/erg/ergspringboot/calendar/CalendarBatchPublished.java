package com.erg.ergspringboot.calendar;

import java.time.OffsetDateTime;

/** Small, immutable module event. Delivery is at-least-once. */
public record CalendarBatchPublished(
        String eventId,
        String tenantId,
        String batchId,
        String actorId,
        OffsetDateTime occurredAt,
        int schemaVersion
) { }
