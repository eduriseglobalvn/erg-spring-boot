package com.erg.ergspringboot.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection;
import com.erg.ergspringboot.model.notifications.NotificationMessageEntity;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.repository.notifications.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.repository.notifications.NotificationMessageRepository;
import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import com.erg.ergspringboot.repository.notifications.NotificationPreferenceRepository;
import com.erg.ergspringboot.repository.notifications.NotificationRecipientRepository;
import com.erg.ergspringboot.service.notifications.NotificationCommand;
import com.erg.ergspringboot.service.notifications.NotificationCommandService;
import com.erg.ergspringboot.service.notifications.NotificationMetrics;
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
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
    void publishedBatchIsAcceptedByTheRealNotificationCommandService() {
        CalendarPublishedBatchProjection calendar = org.mockito.Mockito.mock(CalendarPublishedBatchProjection.class);
        when(calendar.findPublishedBatch("tenant-1", "batch-1"))
                .thenReturn(Optional.of(new CalendarPublishedBatchProjection.PublishedBatch(
                        "batch-1", "school-1", 2, List.of("teacher-1"))));
        NotificationMessageRepository messages = org.mockito.Mockito.mock(NotificationMessageRepository.class);
        NotificationRecipientRepository recipients = org.mockito.Mockito.mock(NotificationRecipientRepository.class);
        when(messages.saveAndFlush(any())).thenAnswer(invocation -> {
            NotificationMessageEntity message = invocation.getArgument(0);
            message.setId("notification-1");
            return message;
        });
        NotificationCommandService realService = new NotificationCommandService(
                messages,
                recipients,
                org.mockito.Mockito.mock(NotificationDeviceRegistrationRepository.class),
                org.mockito.Mockito.mock(NotificationOutboxRepository.class),
                new NotificationPushProperties(),
                new NotificationTokenCipher(new NotificationPushProperties()),
                org.mockito.Mockito.mock(NotificationMetrics.class),
                org.mockito.Mockito.mock(NotificationPreferenceRepository.class));
        CalendarBatchPublishedConsumer consumer = new CalendarBatchPublishedConsumer(calendar, realService);

        consumer.on(new CalendarBatchPublished(
                "schedule-published:batch-1", "tenant-1", "batch-1", "user-1", OffsetDateTime.now(), 1));

        ArgumentCaptor<NotificationMessageEntity> saved = ArgumentCaptor.forClass(NotificationMessageEntity.class);
        verify(messages).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEventType()).isEqualTo("SCHEDULE_PUBLISHED");
        assertThat(saved.getValue().getPayloadData()).containsEntry("batchId", "batch-1").doesNotContainKey("eventType");
        verify(recipients).saveAll(any());
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
