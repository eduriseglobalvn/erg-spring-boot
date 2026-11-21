package com.erg.ergspringboot.calendar.internal.support;

import com.erg.ergspringboot.calendar.CalendarSlotsChanged;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;

/** Tells the other modules that PUBLISHED periods were cancelled, so confirmed month totals are re-checked. */
public final class CalendarCancelAnnouncer {

    private CalendarCancelAnnouncer() {
    }

    /** Pass the events as read BEFORE the cancel; only PUBLISHED ones count (a draft was never on a sheet). One event per batch. */
    public static void announce(ApplicationEventPublisher publisher, String tenantId, Collection<CalendarEvent> before,
            String kind, String actorId) {
        Map<String, List<String>> byBatch = new LinkedHashMap<>();
        for (CalendarEvent e : before) {
            if ("PUBLISHED".equals(e.getStatus()) && tenantId.equals(e.getTenantId())) {
                byBatch.computeIfAbsent(e.getBatchId(), k -> new java.util.ArrayList<>()).add(e.getId());
            }
        }
        OffsetDateTime now = OffsetDateTime.now();
        byBatch.forEach((batchId, ids) -> publisher.publishEvent(new CalendarSlotsChanged(
                "schedule-slots-changed:" + IdGenerator.newId(), tenantId, batchId, kind, ids, List.of(), actorId, now, 1, 1L)));
    }
}
