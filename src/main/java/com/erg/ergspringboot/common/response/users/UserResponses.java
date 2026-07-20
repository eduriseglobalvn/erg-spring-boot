package com.erg.ergspringboot.common.response.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public final class UserResponses {

    private UserResponses() {
    }

    public record UserResponse(
            String id,
            String email,
            String fullName,
            String phone,
            String avatarUrl,
            String bio,
            String gender,
            String dateOfBirth,
            String address,
            String city,
            String district,
            String jobTitle,
            String region,
            String status,
            String provider,
            String accountType,
            String lastLoginProvider,
            List<String> roles,
            Map<String, String> socialLinks,
            boolean isProfileCompleted,
            String lastLoginAt,
            Long loginCount,
            String tenantId,
            String createdAt,
            String updatedAt,
            AccountLifecycleResponse lifecycle
    ) {
    }

    public record UserListResponse(List<UserItemResponse> users, Meta meta) {
    }

    public record UserDetailResponse(
            UserResponse user,
            List<SessionResponse> sessions
    ) {
    }

    public record UserItemResponse(
            String id,
            String email,
            String fullName,
            @JsonProperty("avatar_url") String avatarUrl,
            String phone,
            String status,
            String provider,
            String accountType,
            String lastLoginProvider,
            List<String> roles,
            boolean isProfileCompleted,
            @JsonProperty("last_login_at") String lastLoginAt,
            String createdAt
    ) {
    }

    public record Meta(int page, int limit, long total, long totalPages) {
    }

    public record SessionResponse(
            String sessionId,
            String deviceId,
            String deviceName,
            String portal,
            String ipAddress,
            String userAgent,
            String deviceType,
            boolean current,
            boolean revoked,
            String revokedReason,
            String expiresAt,
            String createdAt,
            String lastSeenAt
    ) {
    }

    public record SessionListResponse(List<SessionResponse> items) {
    }

    public record RevokeSessionResponse(boolean success, String revokedAt) {
    }

    public record ActivityResponse(
            String id,
            @JsonProperty("user_id") String userId,
            String action,
            @JsonProperty("target_user_id") String targetUserId,
            @JsonProperty("ip_address") String ipAddress,
            String description,
            Map<String, Object> metadata,
            String createdAt
    ) {
    }

    public record ActivityListResponse(List<ActivityResponse> items, Meta meta) {
    }

    public record PermissionOverrideResponse(
            String userId,
            List<String> permissions,
            String grantType
    ) {
    }

    public record BulkOperationResponse(@JsonProperty("modified_count") long modifiedCount) {
    }

    public record UserCreatedResponse(
            ProvisionedUserResponse user,
            AccountLifecycleResponse lifecycle,
            String deliveryStatus,
            String oneTimePassword
    ) {
    }

    public record ProvisionedUserResponse(
            String id,
            String primaryEmail,
            String fullName,
            String status
    ) {
    }

    public record AccountLifecycleResponse(
            String status,
            boolean isProfileCompleted,
            String recoveryEmailMasked,
            boolean recoveryEmailVerified,
            boolean mustChangePassword,
            List<String> nextSteps
    ) {
    }

    public record RecoveryEmailChallengeResponse(
            String challengeId,
            String maskedEmail,
            String expiresAt,
            long retryAfterSeconds
    ) {
    }

    public record RecoveryEmailVerificationResponse(
            boolean verified,
            AccountLifecycleResponse lifecycle
    ) {
    }

    public record PasswordChangeResponse(
            AccountLifecycleResponse lifecycle,
            int revokedSessions
    ) {
    }

    public record CredentialDeliveryResponse(
            String deliveryStatus,
            String oneTimePassword
    ) {
    }

    public record OnboardingResponse(
            String id,
            String email,
            String fullName,
            @JsonProperty("avatar_url") String avatarUrl,
            boolean isProfileCompleted
    ) {
    }

    public record MessageResponse(String message) {
    }
}
