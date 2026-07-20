package com.erg.ergspringboot.calendar.internal.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.service.notifications.NotificationCommand;
import com.erg.ergspringboot.service.notifications.NotificationCommandService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CalendarBatchPublishedListenerTest {

    @Test
    void retryUsesTheSameDownstreamIdempotencyKey() {
        CalendarBatchRepository batches = org.mockito.Mockito.mock(CalendarBatchRepository.class);
        CalendarEventRepository events = org.mockito.Mockito.mock(CalendarEventRepository.class);
        CalendarEventTeacherRepository assignments = org.mockito.Mockito.mock(CalendarEventTeacherRepository.class);
        NotificationCommandService notifications = org.mockito.Mockito.mock(NotificationCommandService.class);
        CalendarBatch batch = new CalendarBatch();
        batch.setId("batch-1");
        batch.setTenantId("tenant-1");
        batch.setSchoolId("school-1");
        CalendarEvent calendarEvent = new CalendarEvent();
        calendarEvent.setId("event-1");
        CalendarEventTeacher teacher = new CalendarEventTeacher();
        teacher.setTeacherUserId("teacher-1");
        when(batches.findByTenantIdAndId("tenant-1", "batch-1")).thenReturn(Optional.of(batch));
        when(events.findByTenantIdAndBatchId("tenant-1", "batch-1")).thenReturn(List.of(calendarEvent));
        when(assignments.findByTenantIdAndEventIdIn("tenant-1", List.of("event-1"))).thenReturn(List.of(teacher));
        CalendarBatchPublished event = new CalendarBatchPublished(
                "schedule-published:batch-1", "tenant-1", "batch-1", "user-1", OffsetDateTime.now(), 1);
        CalendarBatchPublishedListener listener = new CalendarBatchPublishedListener(batches, events, assignments, notifications);

        listener.on(event);
        listener.on(event);

        ArgumentCaptor<NotificationCommand> commands = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notifications, org.mockito.Mockito.times(2)).enqueue(commands.capture());
        assertThat(commands.getAllValues()).extracting(NotificationCommand::idempotencyKey)
                .containsOnly("schedule-published:batch-1");
    }
}
