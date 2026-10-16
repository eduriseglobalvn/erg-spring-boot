package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection;
import com.erg.ergspringboot.service.notifications.NotificationCommand;
import com.erg.ergspringboot.service.notifications.NotificationCommandService;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Notifications-owned consumer for the public CalendarBatchPublished event. */
@Component
public class CalendarBatchPublishedConsumer {

    private final CalendarPublishedBatchProjection calendar;
    private final NotificationCommandService notifications;

    public CalendarBatchPublishedConsumer(
            CalendarPublishedBatchProjection calendar,
            NotificationCommandService notifications
    ) {
        this.calendar = calendar;
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(CalendarBatchPublished event) {
        CalendarPublishedBatchProjection.PublishedBatch batch = calendar
                .findPublishedBatch(event.tenantId(), event.batchId())
                .orElseThrow(() -> new IllegalStateException("Published calendar batch no longer exists"));
        if (batch.teacherIds().isEmpty()) {
            return;
        }
        notifications.enqueue(new NotificationCommand(
                event.tenantId(), event.actorId(), event.eventId(),
                "SCHEDULE_PUBLISHED", "teaching_schedule", event.batchId(),
                "Lich giang day da duoc xuat ban",
                "Lich giang day cho " + batch.schoolId() + " da duoc xuat ban voi "
                        + batch.sessionCount() + " buoi hoc.",
                "/calendar", "normal", "lms", batch.teacherIds(), null,
                Map.of("batchId", event.batchId(), "eventType", "SCHEDULE_PUBLISHED",
                        "eventId", event.eventId())));
    }
}
