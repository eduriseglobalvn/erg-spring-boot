package com.erg.ergspringboot.notifications.push;

import java.util.Map;

public record FirebasePushPayload(
        String title,
        String body,
        String actionUrl,
        String collapseKey,
        Map<String, String> data
) {
}
