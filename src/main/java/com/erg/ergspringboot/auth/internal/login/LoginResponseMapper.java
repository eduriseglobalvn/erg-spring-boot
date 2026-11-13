package com.erg.ergspringboot.auth.internal.login;

import com.erg.ergspringboot.common.response.auth.AuthResponses.ProfileResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.SessionDeviceResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure mapping of a completed login into its response parts: profile, lifecycle, session. */
final class LoginResponseMapper {

    private LoginResponseMapper() {
    }

    static ProfileResponse profile(User user, boolean studentOnly) {
        return new ProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(), user.getPhone(),
                user.getStatus(), user.getProvider(), firstNonBlank(user.getAccountType(), "erg"),
                "user", user.getLastLoginProvider(), roles(user.getRoles()),
                Boolean.TRUE.equals(user.getIsProfileCompleted()), toIso(user.getCreatedAt()), lifecycle(user, studentOnly));
    }

    static SessionDeviceResponse sessionResponse(AuthSession session) {
        return new SessionDeviceResponse(
                session.getSessionId(), session.getDeviceId(), session.getDeviceName(), session.getPortal(),
                session.getIpAddress(), session.getUserAgent(), true,
                session.getRevokedAt() != null, session.getRevokedReason(),
                toIso(session.getCreatedAt()), toIso(session.getLastActiveAt()), toIso(session.getExpiresAt()));
    }

    static List<String> roles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("user");
        }
        return roles.stream().map(Role::getName).filter(name -> !isBlank(name)).collect(Collectors.toList());
    }

    /** A student account (STU-01) is never sent to onboarding, password change or email verification. */
    private static AccountLifecycleResponse lifecycle(User user, boolean studentOnly) {
        List<String> nextSteps = new ArrayList<>();
        if (!studentOnly) {
            if (!Boolean.TRUE.equals(user.getIsProfileCompleted())) {
                nextSteps.add("COMPLETE_PROFILE");
            }
            if (!Boolean.TRUE.equals(user.getRecoveryEmailVerified())) {
                nextSteps.add("VERIFY_RECOVERY_EMAIL");
            }
            if (Boolean.TRUE.equals(user.getMustChangePassword())) {
                nextSteps.add("CHANGE_PASSWORD");
            }
        }
        String lifecycleStatus = nextSteps.isEmpty() ? "ACTIVE"
                : ("INVITED".equalsIgnoreCase(user.getStatus())
                ? "PROVISIONED" : "FIRST_LOGIN_RESTRICTED");
        return new AccountLifecycleResponse(
                lifecycleStatus, Boolean.TRUE.equals(user.getIsProfileCompleted()),
                maskEmail(user.getRecoveryEmail()), Boolean.TRUE.equals(user.getRecoveryEmailVerified()),
                Boolean.TRUE.equals(user.getMustChangePassword()), List.copyOf(nextSteps));
    }

    private static String maskEmail(String email) {
        String normalized = isBlank(email) ? null : email.trim();
        if (normalized == null) {
            return null;
        }
        int separator = normalized.indexOf('@');
        if (separator <= 1) {
            return "***";
        }
        return normalized.substring(0, 1) + "***" + normalized.substring(separator);
    }

    private static String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static String toIso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
