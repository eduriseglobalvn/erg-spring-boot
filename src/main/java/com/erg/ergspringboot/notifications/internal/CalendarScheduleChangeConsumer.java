package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.calendar.CalendarBatchCancelled;
import com.erg.ergspringboot.calendar.CalendarEventChanged;
import com.erg.ergspringboot.calendar.CalendarHolidayCancelled;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection;
import com.erg.ergspringboot.calendar.CalendarSlotsChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** SCHEDULE_CHANGED (an edited PUBLISHED period) and SCHEDULE_CANCELLED (a cancelled published batch or periods a day off cancelled). */
@Component
public class CalendarScheduleChangeConsumer {

    private static final Logger log = LoggerFactory.getLogger(CalendarScheduleChangeConsumer.class);

    private final CalendarPublishedBatchProjection calendar;
    private final NotificationCommandService notifications;

    public CalendarScheduleChangeConsumer(
            CalendarPublishedBatchProjection calendar,
            NotificationCommandService notifications
    ) {
        this.calendar = calendar;
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(CalendarEventChanged event) {
        var batch = calendar.findChangedEvent(event.tenantId(), event.calendarEventId()).orElse(null);
        if (batch == null || batch.slots().isEmpty()) {
            log.info("Skip schedule-changed notification {}: period gone or no longer published", event.eventId());
            return;
        }
        // Single-event edits (room/note) never change the people, so current people = old people. Teacher swaps and
        // moved slots publish CalendarSlotsChanged instead (V157), which also tells whoever lost a period.
        notifications.enqueueAll(ScheduleNotificationComposer.compose(ScheduleNotificationComposer.Kind.CHANGED, event.tenantId(),
                        event.actorId(), "schedule-event-changed:" + event.calendarEventId(), event.aggregateVersion(), "SCHEDULE_CHANGED", batch));
    }

    /**
     * Several published periods moved, re-roomed or reassigned: whoever is on them now hears CHANGED once; whoever
     * lost a period (old teacher, removed assistant) hears REMOVED. Keys carry the change id, so a retry never doubles.
     */
    @ApplicationModuleListener
    public void on(CalendarSlotsChanged event) {
        var batch = calendar.findChangedEvents(event.tenantId(), event.batchId(), event.calendarEventIds()).orElse(null);
        if (batch != null && !batch.slots().isEmpty()) {
            notifications.enqueueAll(ScheduleNotificationComposer.compose(ScheduleNotificationComposer.Kind.CHANGED, event.tenantId(), event.actorId(),
                            event.eventId(), event.aggregateVersion(), "SCHEDULE_CHANGED", batch));
        }
        if (batch == null || event.removed().isEmpty()) return;
        var lost = new CalendarPublishedBatchProjection.PublishedBatch(batch.batchId(), batch.schoolId(), batch.schoolName(),
                batch.schoolShortName(), batch.status(), batch.slots().stream().map(slot -> new CalendarPublishedBatchProjection.Slot(
                        slot.eventId(), slot.classId(), slot.className(), slot.startAt(), slot.periodStart(), slot.periodCount(),
                        slot.roomName(), event.removed().stream().filter(r -> r.calendarEventId().equals(slot.eventId()))
                                .map(r -> new CalendarPublishedBatchProjection.Person(r.userId(), r.name(), r.role())).toList()))
                .filter(slot -> !slot.people().isEmpty()).toList());
        notifications.enqueueAll(ScheduleNotificationComposer.compose(ScheduleNotificationComposer.Kind.REMOVED, event.tenantId(), event.actorId(),
                        event.eventId() + ":removed", event.aggregateVersion(), "SCHEDULE_CHANGED", lost));
    }

    @ApplicationModuleListener
    public void on(CalendarHolidayCancelled event) {
        notifications.enqueueAll(HolidayCancelNotificationComposer.compose(event, calendar.findCancelledEvents(event.tenantId(), event.calendarEventIds())));
    }

    @ApplicationModuleListener
    public void on(CalendarBatchCancelled event) {
        var batch = calendar.findCancelledBatch(event.tenantId(), event.batchId(), event.occurredAt()).orElse(null);
        if (batch == null || batch.slots().isEmpty()) {
            log.info("Skip schedule-cancelled notification {}: draft batch or no future period", event.eventId());
            return;
        }
        notifications.enqueueAll(ScheduleNotificationComposer.compose(ScheduleNotificationComposer.Kind.CANCELLED, event.tenantId(),
                        event.actorId(), event.eventId(), event.aggregateVersion(), "SCHEDULE_CANCELLED", batch));
    }
}
