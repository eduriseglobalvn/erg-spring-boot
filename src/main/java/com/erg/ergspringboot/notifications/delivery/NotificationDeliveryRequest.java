package com.erg.ergspringboot.notifications.delivery;

/** Immutable delivery request passed from the outbox worker to a provider adapter. */
public record NotificationDeliveryRequest(
        String tenantId,
        String notificationId,
        String channel,
        String targetKind,
        String provider,
        String targetRef,
        NotificationDeliveryPayload payload
) {
    public NotificationDeliveryKey key() {
        return NotificationDeliveryKey.of(channel, targetKind, provider);
    }
}
