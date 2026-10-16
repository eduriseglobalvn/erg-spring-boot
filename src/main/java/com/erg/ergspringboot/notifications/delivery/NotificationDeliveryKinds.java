package com.erg.ergspringboot.notifications.delivery;

import java.util.LinkedHashSet;
import java.util.Set;

/** Catalog of delivery kinds that can currently be persisted in notification_outbox. */
public final class NotificationDeliveryKinds {

    private static final Set<NotificationDeliveryKey> PERSISTED_KEYS = Set.of(
            NotificationDeliveryKey.of("push", "registration_token", "firebase"),
            NotificationDeliveryKey.of("push", "fid", "firebase"),
            NotificationDeliveryKey.of("push", "topic", "firebase")
    );

    private NotificationDeliveryKinds() { }

    public static Set<NotificationDeliveryKey> persistedKeys() {
        return PERSISTED_KEYS;
    }

    public static Set<NotificationDeliveryKey> copyOf(Iterable<NotificationDeliveryKey> keys) {
        LinkedHashSet<NotificationDeliveryKey> copy = new LinkedHashSet<>();
        keys.forEach(copy::add);
        return Set.copyOf(copy);
    }
}
