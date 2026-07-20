package com.erg.ergspringboot.calendar.internal.notification;

import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.service.notifications.NotificationCommand;
import com.erg.ergspringboot.service.notifications.NotificationCommandService;
import java.util.List;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class CalendarBatchPublishedListener {
    private final CalendarBatchRepository batches;
    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository assignments;
    private final NotificationCommandService notifications;

    CalendarBatchPublishedListener(CalendarBatchRepository batches, CalendarEventRepository events,
            CalendarEventTeacherRepository assignments, NotificationCommandService notifications) {
        this.batches = batches;
        this.events = events;
        this.assignments = assignments;
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    void on(CalendarBatchPublished event) {
        CalendarBatch batch = batches.findByTenantIdAndId(event.tenantId(), event.batchId())
                .orElseThrow(() -> new IllegalStateException("Published calendar batch no longer exists"));
        List<CalendarEvent> calendarEvents = events.findByTenantIdAndBatchId(event.tenantId(), event.batchId());
        List<String> eventIds = calendarEvents.stream().map(CalendarEvent::getId).toList();
        List<String> teacherIds = assignments.findByTenantIdAndEventIdIn(event.tenantId(), eventIds).stream()
                .map(CalendarEventTeacher::getTeacherUserId)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (teacherIds.isEmpty()) return;

        notifications.enqueue(new NotificationCommand(
                event.tenantId(), event.actorId(), event.eventId(),
                "SCHEDULE_PUBLISHED", "teaching_schedule", event.batchId(),
                "Lich giang day da duoc xuat ban",
                "Lich giang day cho " + batch.getSchoolId() + " da duoc xuat ban voi " + calendarEvents.size() + " buoi hoc.",
                "/calendar", "normal", "lms", teacherIds, null,
                Map.of("batchId", event.batchId(), "eventType", "SCHEDULE_PUBLISHED", "eventId", event.eventId())));
    }
}
