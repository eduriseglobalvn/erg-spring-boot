package com.erg.ergspringboot.common.response.notifications;

import com.erg.ergspringboot.common.response.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class NotificationResponses {

    private NotificationResponses() {
    }

    public record NotificationItemResponse(
            String id,
            String recipientId,
            String eventType,
            String sourceType,
            String sourceId,
            String title,
            String bodyPreview,
            String actionUrl,
            String priority,
            String channel,
            String status,
            OffsetDateTime readAt,
            OffsetDateTime createdAt,
            Map<String, String> data
    ) {
    }

    public record NotificationPageResponse(PageResponse<NotificationItemResponse> notifications) {
    }

    public record UnreadCountResponse(long unread) {
    }

    public record MarkReadResponse(boolean read, int updated) {
    }

    public record ArchiveResponse(boolean archived) {
    }

    public record DeviceRegistrationResponse(
            String id,
            String portal,
            String platform,
            String permissionState,
            boolean enabled,
            OffsetDateTime lastSeenAt
    ) {
    }

    public record NotificationPreferenceResponse(
            String eventType,
            boolean pushEnabled,
            boolean inboxEnabled,
            boolean emailEnabled,
            Map<String, String> quietHours
    ) {
    }

    public record AdminSendResponse(
            String notificationId,
            int recipientCount,
            int outboxCount
    ) {
    }

    public record AdminStatsResponse(
            long pending,
            long retry,
            long sent,
            long dead
    ) {
    }

    public record OutboxRunResponse(int processed, int sent, int failed, int retried, int dead) {
    }

    public static PageResponse<NotificationItemResponse> page(List<NotificationItemResponse> items, int page, int size, long total) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / (double) size);
        return new PageResponse<>(items, page, size, total, totalPages, page + 1 < totalPages, page > 0);
    }
}
