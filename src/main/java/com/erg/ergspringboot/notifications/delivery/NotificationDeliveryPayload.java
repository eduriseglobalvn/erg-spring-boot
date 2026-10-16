package com.erg.ergspringboot.notifications.delivery;

import java.util.Map;

/** Provider-neutral notification payload. Provider adapters map this to their wire format. */
public record NotificationDeliveryPayload(
        String title,
        String body,
        String actionUrl,
        String collapseKey,
        Map<String, String> data
) {
    public NotificationDeliveryPayload {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
