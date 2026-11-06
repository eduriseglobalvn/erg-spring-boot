package com.erg.ergspringboot.notifications;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** Response bodies of {@code /api/v1/notification-campaigns}. */
public final class NotificationCampaignResponses {

    private NotificationCampaignResponses() {
    }

    public record CampaignResponse(
            String id,
            String title,
            String body,
            NotificationPriority priority,
            CampaignAudience audience,
            CampaignStatus status,
            @Schema(description = "ADMIN or TEACHER") String senderRole,
            String createdBy,
            boolean requireReadAck,
            boolean pushEnabled,
            Integer estimatedCount,
            Integer resolvedCount,
            OffsetDateTime scheduledAt,
            OffsetDateTime sentAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            long recipientCount,
            @Schema(description = "Recipients who opened the message") long viewedCount,
            @Schema(description = "Recipients who acknowledged reading") long ackedCount) {
    }

    public record CampaignRecipientResponse(
            String userId, String displayName, OffsetDateTime readAt, OffsetDateTime ackedAt) {
    }

    public record CampaignSummaryResponse(
            @Schema(description = "Campaigns sent since the start of the current month (Asia/Ho_Chi_Minh)")
            long sentThisMonth,
            @Schema(description = "Viewed recipients / recipients over campaigns sent this month, 0..1 (0 when none)")
            double avgViewedRate,
            @Schema(description = "Recipients who have not acknowledged, over sent campaigns that require acknowledgement")
            long unackedTotal,
            @Schema(description = "Sent campaigns requiring acknowledgement with at least one recipient not acknowledged")
            long unackedCampaigns) {
    }
}
