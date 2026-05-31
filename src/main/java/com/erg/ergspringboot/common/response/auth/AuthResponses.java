package com.erg.ergspringboot.common.response.auth;

import java.util.List;

public final class AuthResponses {

    private AuthResponses() {
    }

    public record AuthResponse(
            ProfileResponse user,
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType,
            SessionDeviceResponse session,
            List<String> permissions,
            List<String> portals,
            String accountType,
            String accessLevel
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType
    ) {
    }

    public record SessionDeviceResponse(
            String sessionId,
            String deviceId,
            String deviceName,
            String portal,
            String ipAddress,
            String userAgent,
            boolean current,
            boolean revoked,
            String revokedReason,
            String createdAt,
            String lastSeenAt,
            String expiresAt
    ) {
    }

    public record ProfileResponse(
            String id,
            String email,
            String fullName,
            String avatarUrl,
            String phone,
            String status,
            String provider,
            String accountType,
            String accessLevel,
            String googleSub,
            String googleEmail,
            Boolean googleEmailVerified,
            String lastLoginProvider,
            List<String> roles,
            boolean isProfileCompleted,
            String createdAt
    ) {
    }

    public record LoginAttemptResponse(
            String id,
            String tenantId,
            String userId,
            String attemptedEmail,
            String ipAddress,
            String countryCode,
            String countryName,
            String continentCode,
            String userAgent,
            String deviceId,
            String deviceName,
            String portal,
            String result,
            String reason,
            String createdAt
    ) {
    }

    public record IpSecurityStatusResponse(
            String ip,
            boolean blocked,
            boolean allowlisted,
            String countryCode,
            String continentCode,
            boolean geoBlocked,
            long failedAttemptsIp,
            Long failedAttemptsEmail,
            long windowSeconds,
            int threshold
    ) {
    }

    public record AuthErrorResponse(String code, String message) {
    }
}
