package com.erg.ergspringboot.calendar.internal.support;

import com.erg.ergspringboot.calendar.CalendarBatchCancelled;
import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.calendar.CalendarEventChanged;
import com.erg.ergspringboot.calendar.CalendarSlotsChanged;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleDataLoader;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.platform.security.PrincipalCache;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A teacher's cached principal carries the classes of their published timetable, so a timetable change must drop it:
 * the new scope then applies on the next request instead of after the principal cache TTL. Runs inside the writing
 * transaction (plain listener), and {@link PrincipalCache#evictUser} itself waits for the commit.
 */
@Component
class CalendarTeacherScopeEvictor {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarTeacherScopeEvictor.class);

    private final CalendarEventTeacherRepository links;
    private final PrincipalCache principals;

    CalendarTeacherScopeEvictor(CalendarEventTeacherRepository links, PrincipalCache principals) {
        this.links = links;
        this.principals = principals;
    }

    @EventListener
    void onPublished(CalendarBatchPublished event) {
        evict(() -> links.findTeacherIdsByBatch(event.tenantId(), event.batchId()));
    }

    @EventListener
    void onCancelled(CalendarBatchCancelled event) {
        evict(() -> links.findTeacherIdsByBatch(event.tenantId(), event.batchId()));
    }

    @EventListener
    void onSlotsChanged(CalendarSlotsChanged event) {
        Set<String> teachers = new LinkedHashSet<>();
        // a removed person is no longer linked to the period, so only the event carries them
        event.removed().forEach(person -> teachers.add(person.userId()));
        evict(() -> {
            for (var chunk : ScheduleDataLoader.chunks(event.calendarEventIds())) {
                teachers.addAll(links.findTeacherIdsByEventIds(event.tenantId(), chunk));
            }
            return teachers;
        });
    }

    @EventListener
    void onEventChanged(CalendarEventChanged event) {
        evict(() -> links.findTeacherIdsByEventIds(event.tenantId(), java.util.List.of(event.calendarEventId())));
    }

    private void evict(java.util.function.Supplier<Collection<String>> teacherIds) {
        try {
            teacherIds.get().forEach(principals::evictUser);
        } catch (RuntimeException failure) {
            // the timetable write must not fail over a cache; the principal then refreshes when its TTL ends
            LOG.warn("Teacher principal eviction skipped: {}", failure.toString());
        }
    }
}
