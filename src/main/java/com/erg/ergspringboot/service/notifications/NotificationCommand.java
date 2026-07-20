package com.erg.ergspringboot.service.notifications;

import java.util.List;
import java.util.Map;

public record NotificationCommand(
        String tenantId,
        String createdBy,
        String idempotencyKey,
        String eventType,
        String sourceType,
        String sourceId,
        String title,
        String bodyPreview,
        String actionUrl,
        String priority,
        String portal,
        List<String> userIds,
        String topicName,
        Map<String, String> data
) {
}
