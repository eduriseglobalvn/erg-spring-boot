package com.erg.ergspringboot.auth.internal.login;

import com.erg.ergspringboot.common.response.auth.AuthResponses.ProfileResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.SessionDeviceResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.users.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Pure mapping of a completed login into its response parts: profile, lifecycle, session and portals. */
final class LoginResponseMapper {

    private LoginResponseMapper() {
    }

    /**
     * Portals listed in the login response. These are a compatibility/audit hint only; authorization
     * is evaluated later from the verified server-side grants.
     */
    static List<String> entitledPortals(List<String> permissions, List<String> roles) {
        if (permissions.contains("*")) {
            return List.of("*");
        }
        Set<String> portals = new TreeSet<>();
        permissions.forEach(permission -> {
            int separator = permission.indexOf('.');
            if (separator <= 0) {
                return;
            }
            String namespace = permission.substring(0, separator).toLowerCase(Locale.ROOT);
            if (Set.of("admin", "crm", "lcms", "lms", "elearning").contains(namespace)) {
                portals.add(namespace);
            }
        });
        if (roles != null) {
            roles.stream()
                    .map(role -> role == null ? "" : role.trim().toLowerCase(Locale.ROOT))
                    .forEach(role -> {
                        if (role.equals("admin") || role.equals("administrator")
                                || role.equals("system.super_admin") || role.equals("erg_super_admin")
                                || role.equals("global_admin") || role.equals("lms_admin")) {
                            portals.add("admin");
                        } else if (role.equals("teacher") || role.equals("instructor")) {
                            portals.add("lms");
                        } else if (role.equals("student") || role.equals("learner")) {
                            portals.add("elearning");
                        } else if (role.equals("crm") || role.equals("crm_manager")) {
                            portals.add("crm");
                        }
                    });
        }
        return List.copyOf(portals);
    }

    static ProfileResponse profile(User user) {
        return new ProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(), user.getPhone(),
                user.getStatus(), user.getProvider(), firstNonBlank(user.getAccountType(), "erg"),
                "user", user.getLastLoginProvider(), roles(user.getRoles()),
                Boolean.TRUE.equals(user.getIsProfileCompleted()), toIso(user.getCreatedAt()), lifecycle(user));
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

    private static AccountLifecycleResponse lifecycle(User user) {
        List<String> nextSteps = new ArrayList<>();
        if (!Boolean.TRUE.equals(user.getIsProfileCompleted())) {
            nextSteps.add("COMPLETE_PROFILE");
        }
        if (!Boolean.TRUE.equals(user.getRecoveryEmailVerified())) {
            nextSteps.add("VERIFY_RECOVERY_EMAIL");
        }
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            nextSteps.add("CHANGE_PASSWORD");
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
