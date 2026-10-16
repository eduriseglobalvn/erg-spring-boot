package com.erg.ergspringboot.notifications.delivery;

import java.util.Locale;

/** Stable routing key for one persisted notification delivery kind. */
public record NotificationDeliveryKey(String channel, String targetKind, String provider) {

    public NotificationDeliveryKey {
        channel = normalize(channel, "channel");
        targetKind = normalize(targetKind, "targetKind");
        provider = normalize(provider, "provider");
    }

    public static NotificationDeliveryKey of(String channel, String targetKind, String provider) {
        return new NotificationDeliveryKey(channel, targetKind, provider);
    }

    private static String normalize(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
