package com.erg.ergspringboot.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection;
import com.erg.ergspringboot.service.notifications.NotificationCommand;
import com.erg.ergspringboot.service.notifications.NotificationCommandService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CalendarBatchPublishedConsumerTest {

    @Test
    void retriesUseTheSameDownstreamIdempotencyKeyAndPublicProjectionOnly() {
        CalendarPublishedBatchProjection calendar = org.mockito.Mockito.mock(CalendarPublishedBatchProjection.class);
        NotificationCommandService notifications = org.mockito.Mockito.mock(NotificationCommandService.class);
        when(calendar.findPublishedBatch("tenant-1", "batch-1"))
                .thenReturn(Optional.of(new CalendarPublishedBatchProjection.PublishedBatch(
                        "batch-1", "school-1", 2, List.of("teacher-1"))));
        CalendarBatchPublished event = new CalendarBatchPublished(
                "schedule-published:batch-1", "tenant-1", "batch-1", "user-1", OffsetDateTime.now(), 1);
        CalendarBatchPublishedConsumer consumer = new CalendarBatchPublishedConsumer(calendar, notifications);

        consumer.on(event);
        consumer.on(event);

        ArgumentCaptor<NotificationCommand> commands = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notifications, org.mockito.Mockito.times(2)).enqueue(commands.capture());
        assertThat(commands.getAllValues()).extracting(NotificationCommand::idempotencyKey)
                .containsOnly("schedule-published:batch-1");
        assertThat(commands.getAllValues()).extracting(NotificationCommand::userIds)
                .containsOnly(List.of("teacher-1"));
    }

    @Test
    void emptyTeacherProjectionDoesNotEnqueue() {
        CalendarPublishedBatchProjection calendar = org.mockito.Mockito.mock(CalendarPublishedBatchProjection.class);
        NotificationCommandService notifications = org.mockito.Mockito.mock(NotificationCommandService.class);
        when(calendar.findPublishedBatch("tenant-1", "batch-1"))
                .thenReturn(Optional.of(new CalendarPublishedBatchProjection.PublishedBatch(
                        "batch-1", "school-1", 0, List.of())));
        CalendarBatchPublishedConsumer consumer = new CalendarBatchPublishedConsumer(calendar, notifications);

        consumer.on(new CalendarBatchPublished(
                "schedule-published:batch-1", "tenant-1", "batch-1", "user-1", OffsetDateTime.now(), 1));

        org.mockito.Mockito.verifyNoInteractions(notifications);
    }
}
