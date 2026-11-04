package com.erg.ergspringboot.auth.internal.login;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.security.EffectivePermissionResolver;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.auth.AuthTokenService;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;

/**
 * Issues the session and tokens for a verified login. Owns the serialized session mutation and
 * the per-device/IP session replacement policy, including its security events.
 * Runs inside the caller's transaction; it is not a Spring bean and adds no proxy boundary.
 */
final class SessionIssuer {
    private static final String DEFAULT_TENANT = "erg";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration PRIVILEGED_SESSION_TTL = Duration.ofHours(8);
    private static final Duration STANDARD_SESSION_TTL = Duration.ofHours(12);

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final AuthTokenService tokens;
    private final LoginAttemptRecorder attemptRecorder;
    private SecurityEventRepository securityEvents;
    private UserRoleAssignmentRepository roleAssignments;

    private int rapidSessionReplacementThreshold = 5;
    private long rapidSessionReplacementWindowMinutes = 10;

    SessionIssuer(
            UserRepository users,
            AuthSessionRepository sessions,
            AuthTokenService tokens,
            LoginAttemptRecorder attemptRecorder) {
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
        this.attemptRecorder = attemptRecorder;
    }

    void setSecurityEventRepository(SecurityEventRepository securityEvents) {
        this.securityEvents = securityEvents;
    }

    void setUserRoleAssignmentRepository(UserRoleAssignmentRepository roleAssignments) {
        this.roleAssignments = roleAssignments;
    }

    void setRapidSessionReplacement(int threshold, long windowMinutes) {
        this.rapidSessionReplacementThreshold = threshold;
        this.rapidSessionReplacementWindowMinutes = windowMinutes;
    }

    AuthResponse issueTokens(
            User user, AuthClientContext context, String loginProvider, String portal) {
        List<String> permissions = effectivePermissions(user);
        // The requested portal is a compatibility/audit hint only. Shared Auth must issue one
        // session before workspace selection; authorization is evaluated later from the verified
        // server-side grants, never from a hostname, query parameter, or login hint.
        List<String> portals = LoginResponseMapper.entitledPortals(
                permissions, LoginResponseMapper.roles(user.getRoles()));
        String accessToken = tokens.newToken();
        String refreshToken = tokens.newToken();
        OffsetDateTime now = now();
        String tenantId = firstNonBlank(user.getTenantId(), DEFAULT_TENANT);
        lockUserForSessionMutation(user.getId());
        List<AuthSession> activeSessions = activeAccountSessions(user.getId(), tenantId);
        SessionPlatform platform = sessionPlatform(context.deviceId(), context.deviceName(), context.userAgent());
        boolean knownBrowserContext = activeSessions.stream()
                .anyMatch(session -> matchesBrowserContext(session, context));
        boolean knownIpAddress = activeSessions.stream()
                .anyMatch(session -> sameKnownValue(session.getIpAddress(), context.ipAddress()));
        List<AuthSession> replacedSessions = knownBrowserContext || knownIpAddress
                ? List.of()
                : activeSessions;
        revokeSessions(replacedSessions, now, "session_replaced");
        if (!replacedSessions.isEmpty()) {
            recordSecurityEvent(user, context, portal, "SESSION_REPLACED", "MEDIUM",
                    "active sessions replaced by a new browser context from an unknown IP");
            recordRapidSessionReplacementWarningIfRequired(user, context, portal);
        }

        AuthSession session = new AuthSession();
        session.setId(IdGenerator.newId());
        session.setUserId(user.getId());
        session.setSessionId(IdGenerator.newId());
        session.setDeviceId(firstNonBlank(context.deviceId(), "web"));
        session.setDeviceName(firstNonBlank(context.deviceName(), "Browser"));
        session.setPlatform(platform.name());
        session.setDeviceFingerprint(trimToNull(context.deviceFingerprint()));
        session.setPortal(portal);
        session.setIpAddress(context.ipAddress());
        session.setUserAgent(context.userAgent());
        session.setAccessTokenHash(tokens.hashToken(accessToken));
        session.setRefreshTokenHash(tokens.hashToken(refreshToken));
        session.setTenantId(tenantId);
        session.setLastActiveAt(now);
        session.setExpiresAt(now.plus(sessionTtl(user)));
        session.setAccessTokenExpiresAt(now.plus(ACCESS_TOKEN_TTL));
        sessions.save(session);
        attemptRecorder.recordLoginAttempt(
                user.getId(), user.getEmail(), context, portal, "SUCCESS", "login_success");

        user.setLastLoginProvider(loginProvider);
        user.setLastLoginAt(now);
        user.setLoginCount(user.getLoginCount() == null ? 1L : user.getLoginCount() + 1);
        users.save(user);

        return new AuthResponse(
                LoginResponseMapper.profile(user), accessToken, refreshToken, ACCESS_TOKEN_TTL.toSeconds(),
                "Bearer", LoginResponseMapper.sessionResponse(session), permissions, portals,
                firstNonBlank(user.getAccountType(), "erg"), "user");
    }

    private void lockUserForSessionMutation(String userId) {
        try {
            if (users.lockIdForSessionMutation(userId).isEmpty()) {
                throw new AuthFailure.InvalidCredentials();
            }
        } catch (QueryTimeoutException | PessimisticLockingFailureException exception) {
            throw new AuthFailure.LoginInProgress();
        }
    }

    private List<String> effectivePermissions(User user) {
        List<UserRoleAssignment> assignments = roleAssignments == null || user.getId() == null
                ? List.of()
                : roleAssignments.findActiveForUser(
                        user.getId(), firstNonBlank(user.getTenantId(), DEFAULT_TENANT), now());
        return EffectivePermissionResolver.resolve(user, assignments, now()).granted();
    }

    private Duration sessionTtl(User user) {
        boolean privileged = user.getRoles() != null && user.getRoles().stream()
                .map(Role::getName)
                .filter(Objects::nonNull)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.contains("admin") || name.contains("manager")
                        || name.contains("coordinator"));
        return privileged ? PRIVILEGED_SESSION_TTL : STANDARD_SESSION_TTL;
    }

    private void recordSecurityEvent(
            User user, AuthClientContext context, String portal,
            String eventType, String severity, String reason) {
        if (securityEvents == null) {
            return;
        }
        SecurityEvent event = new SecurityEvent();
        event.setId(IdGenerator.newId());
        event.setTenantId(firstNonBlank(user.getTenantId(), DEFAULT_TENANT));
        event.setUserId(user.getId());
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setPortal(portal);
        event.setDeviceId(context.deviceId());
        event.setIpAddress(context.ipAddress());
        event.setReason(reason);
        event.setPlatform(sessionPlatform(
                context.deviceId(), context.deviceName(), context.userAgent()).name());
        event.setResult("RAPID_SESSION_REPLACEMENT".equals(eventType) ? "WARNING"
                : eventType.endsWith("FORBIDDEN") ? "DENIED" : "SUCCESS");
        event.setRiskCode("SESSION_REPLACED".equals(eventType)
                || "RAPID_SESSION_REPLACEMENT".equals(eventType)
                || eventType.endsWith("FORBIDDEN") ? eventType : null);
        event.setMetadata(Map.of(
                "deviceName", firstNonBlank(context.deviceName(), "Browser"),
                "portal", portal));
        event.setOccurredAt(now());
        securityEvents.save(event);
    }

    private void recordRapidSessionReplacementWarningIfRequired(
            User user, AuthClientContext context, String portal) {
        if (securityEvents == null) {
            return;
        }
        String tenantId = firstNonBlank(user.getTenantId(), DEFAULT_TENANT);
        long replacementCount = securityEvents.countByTenantIdAndUserIdAndEventTypeAndOccurredAtAfter(
                tenantId, user.getId(), "SESSION_REPLACED",
                now().minusMinutes(rapidSessionReplacementWindowMinutes));
        if (replacementCount < rapidSessionReplacementThreshold) {
            return;
        }
        recordSecurityEvent(user, context, portal, "RAPID_SESSION_REPLACEMENT", "HIGH",
                "session replacement threshold reached; warning only");
    }

    private SessionPlatform sessionPlatform(String deviceId, String deviceName, String userAgent) {
        String value = String.join(" ", firstNonBlank(deviceId, ""),
                firstNonBlank(deviceName, ""), firstNonBlank(userAgent, ""))
                .toLowerCase(Locale.ROOT);
        return value.matches(".*(mobile|android|iphone|ipad|tablet|pwa).*\\b.*")
                ? SessionPlatform.WEB_MOBILE : SessionPlatform.WEB_DESKTOP;
    }

    private boolean matchesBrowserContext(AuthSession session, AuthClientContext context) {
        if (!sameKnownValue(session.getDeviceId(), context.deviceId())) {
            return false;
        }
        String storedFingerprint = trimToNull(session.getDeviceFingerprint());
        String incomingFingerprint = trimToNull(context.deviceFingerprint());
        if (storedFingerprint != null && incomingFingerprint != null) {
            return storedFingerprint.equals(incomingFingerprint);
        }
        return sameKnownValue(session.getDeviceName(), context.deviceName())
                && sameKnownValue(session.getUserAgent(), context.userAgent());
    }

    private boolean sameKnownValue(String left, String right) {
        String normalizedLeft = trimToNull(left);
        String normalizedRight = trimToNull(right);
        return normalizedLeft != null && normalizedRight != null
                && normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    private List<AuthSession> activeAccountSessions(String userId, String tenantId) {
        List<AuthSession> activeSessions = sessions.findByUserIdAndTenantIdAndRevokedAtIsNull(userId, tenantId);
        if (activeSessions == null || activeSessions.isEmpty()) {
            return List.of();
        }
        return activeSessions.stream().filter(this::isSessionActive).toList();
    }

    private void revokeSessions(List<AuthSession> activeSessions, OffsetDateTime revokedAt, String reason) {
        if (activeSessions.isEmpty()) {
            return;
        }
        activeSessions.forEach(session -> {
            session.setRevokedAt(revokedAt);
            session.setRevokedReason(reason);
        });
        sessions.saveAll(activeSessions);
    }

    private boolean isSessionActive(AuthSession session) {
        return session.getRevokedAt() == null && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(now());
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private enum SessionPlatform {
        WEB_DESKTOP,
        WEB_MOBILE
    }
}
