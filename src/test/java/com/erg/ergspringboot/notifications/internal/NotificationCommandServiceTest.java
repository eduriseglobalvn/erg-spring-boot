package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationPreferenceEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationRecipientEntity;
import com.erg.ergspringboot.notifications.NotificationTokenCipher;
import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationPreferenceRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationRecipientRepository;
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
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Test
    void campaignBodyPreviewNeverSplitsAnEmojiAtTheLimit() {
        String emojiAcrossTheLimit = "a".repeat(499) + "😀" + "tail";

        String preview = NotificationCommandService.bodyPreview(emojiAcrossTheLimit);

        assertThat(preview).isEqualTo("a".repeat(499));
        assertThat(NotificationCommandService.bodyPreview("a".repeat(498) + "😀" + "tail"))
                .hasSize(500).endsWith("😀");
        assertThat(NotificationCommandService.bodyPreview("ngắn")).isEqualTo("ngắn");
    }

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
                metrics,
                preferenceRepository
        );
    }

    @Test
    void enqueueHonoursPerEventTypeInboxAndPushOptOuts() {
        when(messageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            NotificationMessageEntity message = invocation.getArgument(0);
            message.setId("notification-1");
            return message;
        });
        when(preferenceRepository.findByTenantIdAndPortalAndEventTypeAndUserIdIn(
                eq("tenant_a"), eq("lms"), eq("SCHEDULE_PUBLISHED"), any()))
                .thenReturn(List.of(
                        preference("inbox-off", false, true),
                        preference("push-off", true, false)));
        when(deviceRepository.findByTenantIdAndUserIdInAndPortalAndEnabledTrue(eq("tenant_a"), any(), eq("lms")))
                .thenReturn(List.of(device("device-inbox-off", "inbox-off"), device("device-push-off", "push-off")));

        NotificationCommandResult result = service().enqueue(command(
                List.of("inbox-off", "push-off"), null, Map.of()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationRecipientEntity>> recipients = ArgumentCaptor.forClass(List.class);
        verify(recipientRepository).saveAll(recipients.capture());
        assertThat(recipients.getValue()).extracting(NotificationRecipientEntity::getUserId).containsExactly("push-off");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationOutboxEntity>> outboxes = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).saveAll(outboxes.capture());
        assertThat(outboxes.getValue()).singleElement()
                .satisfies(outbox -> assertThat(outbox.getPayload()).containsEntry("deviceRegistrationId", "device-inbox-off"));
        assertThat(result.recipientCount()).isEqualTo(1);
        assertThat(result.outboxCount()).isEqualTo(1);
    }

    @Test
    void webPushDevicesAreRoutedToTheVapidAdapterByDeviceIdAndFcmDevicesStayOnFirebase() {
        when(messageRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            NotificationMessageEntity message = invocation.getArgument(0);
            message.setId("notification-1");
            return message;
        });
        NotificationDeviceRegistrationEntity browser = device("device-web", "user-1");
        browser.setTransport("WEB_PUSH");
        when(deviceRepository.findByTenantIdAndUserIdInAndPortalAndEnabledTrue(eq("tenant_a"), any(), eq("lms")))
                .thenReturn(List.of(browser, device("device-fcm", "user-1")));

        service().enqueue(command(List.of("user-1"), null, Map.of()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationOutboxEntity>> outboxes = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).saveAll(outboxes.capture());
        assertThat(outboxes.getValue()).extracting(NotificationOutboxEntity::getTargetKind,
                        NotificationOutboxEntity::getProvider, NotificationOutboxEntity::getTargetRef)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("web_push", "vapid", "device-web"),
                        org.assertj.core.groups.Tuple.tuple("registration_token", "firebase", "token-device-fcm"));
    }

    private NotificationPreferenceEntity preference(String userId, boolean inbox, boolean push) {
        NotificationPreferenceEntity preference = new NotificationPreferenceEntity();
        preference.setTenantId("tenant_a");
        preference.setUserId(userId);
        preference.setPortal("lms");
        preference.setEventType("SCHEDULE_PUBLISHED");
        preference.setInboxEnabled(inbox);
        preference.setPushEnabled(push);
        return preference;
    }

    private NotificationDeviceRegistrationEntity device(String id, String userId) {
        NotificationDeviceRegistrationEntity device = new NotificationDeviceRegistrationEntity();
        device.setId(id);
        device.setTenantId("tenant_a");
        device.setUserId(userId);
        device.setPortal("lms");
        device.setRegistrationToken("token-" + id);
        device.setEnabled(true);
        return device;
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
