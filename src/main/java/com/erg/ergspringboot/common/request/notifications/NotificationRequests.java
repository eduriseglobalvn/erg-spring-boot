package com.erg.ergspringboot.common.request.notifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class NotificationRequests {

    private NotificationRequests() {
    }

    public record DeviceClientRequest(
            String browserName,
            String browserVersion,
            String osName,
            String userAgent
    ) {
    }

    public record RegisterDeviceRequest(
            @NotBlank String portal,
            @NotBlank String platform,
            String firebaseInstallationId,
            String registrationToken,
            String endpoint,
            String serviceWorkerScope,
            String permissionState,
            String locale,
            String timezone,
            DeviceClientRequest client
    ) {
    }

    public record UnregisterDeviceRequest(
            String portal,
            String firebaseInstallationId,
            String registrationToken
    ) {
    }

    public record HeartbeatDeviceRequest(
            String portal,
            String deviceRegistrationId,
            String firebaseInstallationId,
            String registrationToken,
            String permissionState
    ) {
    }

    public record PreferenceUpdateItemRequest(
            @NotBlank String eventType,
            Boolean pushEnabled,
            Boolean inboxEnabled,
            Boolean emailEnabled,
            Map<String, String> quietHours
    ) {
    }

    public record UpdatePreferencesRequest(
            @NotBlank String portal,
            List<PreferenceUpdateItemRequest> preferences
    ) {
    }

    public record AdminSendRequest(
            String idempotencyKey,
            @NotBlank String eventType,
            String sourceType,
            String sourceId,
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 500) String bodyPreview,
            String actionUrl,
            String priority,
            String portal,
            List<String> userIds,
            String topicName,
            Map<String, String> data
    ) {
    }
}
