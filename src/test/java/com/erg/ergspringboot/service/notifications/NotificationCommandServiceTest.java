package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.notifications.NotificationMessageEntity;
import com.erg.ergspringboot.model.notifications.NotificationOutboxEntity;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.repository.notifications.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.repository.notifications.NotificationMessageRepository;
import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import com.erg.ergspringboot.repository.notifications.NotificationRecipientRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceTest {

    @Mock
    private NotificationMessageRepository messageRepository;

    @Mock
    private NotificationRecipientRepository recipientRepository;

    @Mock
    private NotificationDeviceRegistrationRepository deviceRepository;

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private NotificationMetrics metrics;

    @Test
    void enqueueRejectsTopicOutsideTenantNamespace() {
        NotificationCommandService service = service();
        NotificationCommand command = command(List.of(), "t_tenant_b_portal_lms", Map.of("kind", "schedule"));

        assertThatThrownBy(() -> service.enqueue(command))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tenant namespace");
        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void enqueueRejectsReservedDataKeys() {
        NotificationCommandService service = service();
        NotificationCommand command = command(List.of("teacher-1"), null, Map.of("tenantId", "tenant-b"));

        assertThatThrownBy(() -> service.enqueue(command))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reserved");
        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void enqueueCreatesTenantScopedTopicOutbox() {
        when(messageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            NotificationMessageEntity message = invocation.getArgument(0);
            message.setId("notification-1");
            return message;
        });

        NotificationCommandService service = service();
        NotificationCommandResult result = service.enqueue(command(
                List.of(),
                "t_tenant_a_portal_lms",
                Map.of("kind", "schedule")
        ));

        ArgumentCaptor<NotificationOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(NotificationOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        NotificationOutboxEntity outbox = outboxCaptor.getValue();

        assertThat(result.notificationId()).isEqualTo("notification-1");
        assertThat(result.recipientCount()).isZero();
        assertThat(result.outboxCount()).isEqualTo(1);
        assertThat(outbox.getTenantId()).isEqualTo("tenant_a");
        assertThat(outbox.getTargetKind()).isEqualTo("topic");
        assertThat(outbox.getTargetRef()).isEqualTo("t_tenant_a_portal_lms");
        assertThat(outbox.getPayload())
                .containsEntry("notificationId", "notification-1")
                .containsEntry("tenantId", "tenant_a")
                .containsEntry("portal", "lms")
                .containsEntry("kind", "schedule");
    }

    private NotificationCommandService service() {
        return new NotificationCommandService(
                messageRepository,
                recipientRepository,
                deviceRepository,
                outboxRepository,
                new NotificationPushProperties(),
                new NotificationTokenCipher(new NotificationPushProperties()),
                metrics
        );
    }

    private NotificationCommand command(List<String> userIds, String topicName, Map<String, String> data) {
        return new NotificationCommand(
                "tenant_a",
                "admin-1",
                null,
                "SCHEDULE_PUBLISHED",
                "schedule",
                "schedule-1",
                "Lich hoc moi",
                "Giao vien co lich moi",
                "/lms/schedule",
                "normal",
                "lms",
                userIds,
                topicName,
                data
        );
    }
}
