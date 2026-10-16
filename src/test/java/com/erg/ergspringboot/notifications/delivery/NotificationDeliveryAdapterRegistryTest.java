package com.erg.ergspringboot.notifications.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.erg.ergspringboot.notifications.push.SendResult;
import com.erg.ergspringboot.notifications.push.FirebasePushPayload;
import com.erg.ergspringboot.notifications.push.FirebasePushSender;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationDeliveryAdapterRegistryTest {

    private static final NotificationDeliveryKey KEY =
            NotificationDeliveryKey.of("push", "registration_token", "test");

    @Test
    void rejectsDuplicateHandlersForOnePersistedKey() {
        NotificationDeliveryAdapter first = adapter(KEY);
        NotificationDeliveryAdapter second = adapter(KEY);

        assertThatIllegalStateException()
                .isThrownBy(() -> new NotificationDeliveryAdapterRegistry(List.of(first, second), Set.of(KEY)))
                .withMessageContaining("Duplicate notification delivery adapter");
    }

    @Test
    void rejectsPersistedKindsWithoutAHandler() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new NotificationDeliveryAdapterRegistry(List.of(), Set.of(KEY)))
                .withMessageContaining("No notification delivery adapter");
    }

    @Test
    void returnsFailClosedResultForUnknownRuntimeKey() {
        NotificationDeliveryAdapterRegistry registry = new NotificationDeliveryAdapterRegistry(
                List.of(adapter(KEY)), Set.of());
        NotificationDeliveryRequest request = new NotificationDeliveryRequest(
                "tenant", "notification", "email", "mailbox", "unknown", "target",
                new NotificationDeliveryPayload("title", "body", null, null, java.util.Map.of()));

        SendResult result = registry.deliver(request);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("DELIVERY_ADAPTER_MISSING");
    }

    @Test
    void exposesAllFirebasePersistedKindsThroughTheSingleAdapter() {
        NotificationDeliveryAdapter adapter = new FirebaseNotificationDeliveryAdapter(
                new FirebasePushSender() {
                    @Override
                    public SendResult sendToRegistrationToken(FirebasePushPayload payload, String token) {
                        return SendResult.success(200, "id", 1);
                    }

                    @Override
                    public SendResult sendToFid(FirebasePushPayload payload, String fid) {
                        return SendResult.success(200, "id", 1);
                    }

                    @Override
                    public SendResult sendToTopic(FirebasePushPayload payload, String topic) {
                        return SendResult.success(200, "id", 1);
                    }
                },
                new com.erg.ergspringboot.service.notifications.NotificationTokenCipher(
                        new com.erg.ergspringboot.notifications.config.NotificationPushProperties()));

        NotificationDeliveryAdapterRegistry registry = new NotificationDeliveryAdapterRegistry(List.of(adapter));

        assertThat(registry.keys()).containsExactlyInAnyOrderElementsOf(NotificationDeliveryKinds.persistedKeys());
    }

    private NotificationDeliveryAdapter adapter(NotificationDeliveryKey key) {
        return new NotificationDeliveryAdapter() {
            @Override
            public Set<NotificationDeliveryKey> keys() {
                return Set.of(key);
            }

            @Override
            public SendResult deliver(NotificationDeliveryRequest request) {
                return SendResult.success(200, "id", 1);
            }
        };
    }
}
