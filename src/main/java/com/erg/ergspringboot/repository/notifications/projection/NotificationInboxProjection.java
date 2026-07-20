package com.erg.ergspringboot.repository.notifications.projection;

import java.time.OffsetDateTime;
import java.util.Map;

public record NotificationInboxProjection(
        String recipientId,
        String notificationId,
        String tenantId,
        String userId,
        String portal,
        String recipientStatus,
        OffsetDateTime readAt,
        OffsetDateTime archivedAt,
        String eventType,
        String sourceType,
        String sourceId,
        String title,
        String bodyPreview,
        String actionUrl,
        String priority,
        String channel,
        Map<String, String> payloadData,
        OffsetDateTime createdAt
) {
}
