package com.erg.ergspringboot.notifications;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Request bodies of {@code /api/v1/notification-campaigns}. */
public final class NotificationCampaignRequests {

    private NotificationCampaignRequests() {
    }

    public record CampaignDraftRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String body,
            @Schema(description = "INFO | IMPORTANT | URGENT (default INFO)") String priority,
            @NotNull CampaignAudience audience,
            boolean requireReadAck,
            @Schema(description = "Send a push to the recipients' devices too (default true)") Boolean pushEnabled) {
    }

    public record ScheduleCampaignRequest(
            @Schema(description = "Must be in (now, now + 30 days]") @NotNull OffsetDateTime scheduledAt) {
    }
}
