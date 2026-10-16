package com.erg.ergspringboot.auth.internal.login;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.ProfileResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.SessionDeviceResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.security.ErgAuthProperties;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.service.auth.AuthLoginRateLimiter;
import com.erg.ergspringboot.service.auth.AuthTokenService;
import com.erg.ergspringboot.service.auth.LoginFailureEnforcementService;
import com.erg.ergspringboot.service.auth.PasswordService;
import com.erg.ergspringboot.utils.IdGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns login policy, entitlement resolution, serialized session mutation and token issuance. */
@Service
public class AuthLoginUseCase {

    private static final String DEFAULT_TENANT = "erg";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String STATUS_BANNED = "BANNED";
    private static final String PROVIDER_LOCAL = "local";
    private static final String DEFAULT_PORTAL = "lms";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration PRIVILEGED_SESSION_TTL = Duration.ofHours(8);
    private static final Duration STANDARD_SESSION_TTL = Duration.ofHours(12);
    private static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final int LOGIN_ATTEMPT_THRESHOLD = 5;
    private static final List<String> FAILED_LOGIN_RESULTS = List.of("FAILED", "FAILURE");

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final AuthLoginAttemptRepository attempts;
    private final PasswordService passwords;
    private final AuthTokenService tokens;
    private final Optional<MeterRegistry> meterRegistry;
    private SecurityEventRepository securityEvents;
    private LoginFailureEnforcementService failureEnforcement;
    private AuthLoginRateLimiter rateLimiter;
    private UserRoleAssignmentRepository roleAssignments;

    private int rapidSessionReplacementThreshold = 5;
    private long rapidSessionReplacementWindowMinutes = 10;

    /** Compatibility constructor retained for legacy tests and callers wiring without metrics. */
    public AuthLoginUseCase(
            UserRepository users,
            AuthSessionRepository sessions,
            AuthLoginAttemptRepository attempts,
            PasswordService passwords,
            AuthTokenService tokens) {
        this(users, sessions, attempts, passwords, tokens, Optional.empty());
    }

    @Autowired
    public AuthLoginUseCase(
            UserRepository users,
            AuthSessionRepository sessions,
            AuthLoginAttemptRepository attempts,
            PasswordService passwords,
            AuthTokenService tokens,
            ObjectProvider<MeterRegistry> meterRegistry) {
        this(users, sessions, attempts, passwords, tokens,
                Optional.ofNullable(meterRegistry.getIfAvailable()));
    }

    private AuthLoginUseCase(
            UserRepository users,
            AuthSessionRepository sessions,
            AuthLoginAttemptRepository attempts,
            PasswordService passwords,
            AuthTokenService tokens,
            Optional<MeterRegistry> meterRegistry) {
        this.users = users;
        this.sessions = sessions;
        this.attempts = attempts;
        this.passwords = passwords;
        this.tokens = tokens;
        this.meterRegistry = meterRegistry;
    }

    @Autowired(required = false)
    public void setSecurityEventRepository(SecurityEventRepository securityEvents) {
        this.securityEvents = securityEvents;
    }

    @Autowired
    public void setAuthProperties(ErgAuthProperties properties) {
        this.rapidSessionReplacementThreshold = properties.getRapidSessionReplacement().getThreshold();
        this.rapidSessionReplacementWindowMinutes = properties.getRapidSessionReplacement().getWindowMinutes();
    }

    @Autowired(required = false)
    public void setLoginFailureEnforcementService(LoginFailureEnforcementService failureEnforcement) {
        this.failureEnforcement = failureEnforcement;
    }

    @Autowired(required = false)
    public void setAuthLoginRateLimiter(AuthLoginRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Autowired(required = false)
    public void setUserRoleAssignmentRepository(UserRoleAssignmentRepository roleAssignments) {
        this.roleAssignments = roleAssignments;
    }

    @Transactional
    public AuthResponse login(LoginRequest request, AuthClientContext context) {
        String identifier = normalizeIdentifier(request.identifier());
        String portal = normalizePortal(request.portal());
        if (rateLimiter != null && !rateLimiter.allow(identifier, context).allowed()) {
            throw new AuthFailure.TooManyAttempts();
        }
        User user = users.findForLogin(identifier)
                .orElseThrow(() -> {
                    passwords.burnUnknownAccountCheck(request.password());
                    recordFailure(null, identifier, context, portal, "invalid_credentials", false);
                    return new AuthFailure.InvalidCredentials();
                });
        String email = user.getEmail();

        if (isLocked(user)) {
            recordFailure(user, email, context, portal, "account_locked", false);
            throw new AuthFailure.AccountLocked();
        }
        if ("INACTIVE".equalsIgnoreCase(user.getStatus())) {
            recordFailure(user, email, context, portal, "account_disabled", false);
            throw new AuthFailure.AccountDisabled();
        }
        boolean restrictedFirstLogin = "INVITED".equalsIgnoreCase(user.getStatus())
                && Boolean.TRUE.equals(user.getMustChangePassword());
        if ("INVITED".equalsIgnoreCase(user.getStatus()) && !restrictedFirstLogin) {
            recordFailure(user, email, context, portal, "account_activation_required", false);
            throw new AuthFailure.AccountActivationRequired();
        }
        if (STATUS_PENDING.equalsIgnoreCase(user.getStatus())
                && PROVIDER_LOCAL.equalsIgnoreCase(user.getProvider())) {
            recordFailure(user, email, context, portal, "email_not_verified", false);
            throw new AuthFailure.EmailNotVerified();
        }
        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            recordFailure(user, email, context, portal, "invalid_credentials", true);
            throw new AuthFailure.InvalidCredentials();
        }

        if (restrictedFirstLogin) {
            user.setStatus("ACTIVE");
            users.save(user);
        }
        return issueTokens(user, context, "erg", portal);
    }

    private AuthResponse issueTokens(
            User user, AuthClientContext context, String loginProvider, String portal) {
        List<String> permissions = effectivePermissions(user);
        // The requested portal is a compatibility/audit hint only. Shared Auth must issue one
        // session before workspace selection; authorization is evaluated later from the verified
        // server-side grants, never from a hostname, query parameter, or login hint.
        List<String> portals = entitledPortals(permissions, roles(user.getRoles()));
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
        recordLoginAttempt(user.getId(), user.getEmail(), context, portal, "SUCCESS", "login_success");

        user.setLastLoginProvider(loginProvider);
        user.setLastLoginAt(now);
        user.setLoginCount(user.getLoginCount() == null ? 1L : user.getLoginCount() + 1);
        users.save(user);

        return new AuthResponse(
                profile(user), accessToken, refreshToken, ACCESS_TOKEN_TTL.toSeconds(), "Bearer",
                sessionResponse(session), permissions, portals,
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

    private void recordLoginAttempt(
            String userId, String email, AuthClientContext context,
            String portal, String result, String reason) {
        AuthLoginAttempt attempt = new AuthLoginAttempt();
        attempt.setId(IdGenerator.newId());
        attempt.setTenantId(DEFAULT_TENANT);
        attempt.setUserId(userId);
        attempt.setAttemptedEmail(email == null ? null : normalizeEmail(email));
        attempt.setIpAddress(context.ipAddress());
        attempt.setUserAgent(context.userAgent());
        attempt.setDeviceId(firstNonBlank(context.deviceId(), "web"));
        attempt.setDeviceName(firstNonBlank(context.deviceName(), "Browser"));
        attempt.setPortal(portal);
        attempt.setResult(result);
        attempt.setReason(reason);
        attempt.setCreatedAt(now());
        attempts.save(attempt);
        meterRegistry.ifPresent(registry -> registry
                .counter("erg.auth.login.count", "result", result == null ? "none" : result)
                .increment());
    }

    private void recordFailure(
            User user, String email, AuthClientContext context,
            String portal, String reason, boolean enforceThreshold) {
        if (failureEnforcement != null) {
            if (enforceThreshold && "invalid_credentials".equals(reason)) {
                failureEnforcement.recordInvalidCredentials(user, email, context, portal);
            } else {
                failureEnforcement.recordFailure(user, email, context, portal, reason, enforceThreshold);
            }
            return;
        }
        recordLoginAttempt(user == null ? null : user.getId(), email, context, portal, "FAILED", reason);
        if (enforceThreshold && user != null) {
            blockUserIfFailedLoginThresholdReached(user, email, context);
        }
    }

    private void blockUserIfFailedLoginThresholdReached(
            User user, String email, AuthClientContext context) {
        OffsetDateTime since = now().minus(LOGIN_ATTEMPT_WINDOW);
        boolean emailExceeded = attempts.countByAttemptedEmailIgnoreCaseAndResultInAndCreatedAtAfter(
                email, FAILED_LOGIN_RESULTS, since) >= LOGIN_ATTEMPT_THRESHOLD;
        boolean ipExceeded = !isBlank(context.ipAddress())
                && attempts.countByIpAddressAndResultInAndCreatedAtAfter(
                context.ipAddress(), FAILED_LOGIN_RESULTS, since) >= LOGIN_ATTEMPT_THRESHOLD;
        if (!emailExceeded && !ipExceeded) {
            return;
        }
        // Durable account state must not be mutated from attacker-controlled failures.
    }

    private List<String> effectivePermissions(User user) {
        Set<String> permissions = new TreeSet<>();
        if (user.getRoles() != null) {
            user.getRoles().stream()
                    .filter(java.util.Objects::nonNull)
                    .flatMap(role -> role.getPermissions() == null
                            ? java.util.stream.Stream.empty() : role.getPermissions().stream())
                    .map(permission -> permission == null ? null : trimToNull(permission.getName()))
                    .filter(java.util.Objects::nonNull)
                    .forEach(permissions::add);
        }
        if (roleAssignments != null && user.getId() != null) {
            roleAssignments.findActiveForUser(
                            user.getId(), firstNonBlank(user.getTenantId(), DEFAULT_TENANT), now())
                    .stream()
                    .map(UserRoleAssignment::getRole)
                    .filter(java.util.Objects::nonNull)
                    .flatMap(role -> role.getPermissions() == null
                            ? java.util.stream.Stream.empty() : role.getPermissions().stream())
                    .map(permission -> permission == null ? null : trimToNull(permission.getName()))
                    .filter(java.util.Objects::nonNull)
                    .forEach(permissions::add);
        }
        if (user.getPermissionOverrides() != null) {
            user.getPermissionOverrides().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(override -> override.getExpiresAt() == null || override.getExpiresAt().isAfter(now()))
                    .forEach(override -> {
                        String permission = trimToNull(override.getPermission());
                        if (permission == null) {
                            return;
                        }
                        if ("deny".equalsIgnoreCase(override.getGrantType())) {
                            permissions.remove(permission);
                        } else if ("grant".equalsIgnoreCase(override.getGrantType())) {
                            permissions.add(permission);
                        }
                    });
        }
        return List.copyOf(permissions);
    }

    private List<String> entitledPortals(List<String> permissions, List<String> roles) {
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

    private Duration sessionTtl(User user) {
        boolean privileged = user.getRoles() != null && user.getRoles().stream()
                .map(Role::getName)
                .filter(java.util.Objects::nonNull)
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
        event.setMetadata(java.util.Map.of(
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

    private ProfileResponse profile(User user) {
        return new ProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(), user.getPhone(),
                user.getStatus(), user.getProvider(), firstNonBlank(user.getAccountType(), "erg"),
                "user", user.getLastLoginProvider(), roles(user.getRoles()),
                Boolean.TRUE.equals(user.getIsProfileCompleted()), toIso(user.getCreatedAt()), lifecycle(user));
    }

    private AccountLifecycleResponse lifecycle(User user) {
        List<String> nextSteps = new java.util.ArrayList<>();
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

    private SessionDeviceResponse sessionResponse(AuthSession session) {
        return new SessionDeviceResponse(
                session.getSessionId(), session.getDeviceId(), session.getDeviceName(), session.getPortal(),
                session.getIpAddress(), session.getUserAgent(), true,
                session.getRevokedAt() != null, session.getRevokedReason(),
                toIso(session.getCreatedAt()), toIso(session.getLastActiveAt()), toIso(session.getExpiresAt()));
    }

    private List<String> roles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("user");
        }
        return roles.stream().map(Role::getName).filter(name -> !isBlank(name)).collect(Collectors.toList());
    }

    private boolean isSessionActive(AuthSession session) {
        return session.getRevokedAt() == null && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(now());
    }

    private boolean isLocked(User user) {
        return STATUS_BLOCKED.equalsIgnoreCase(user.getStatus())
                || STATUS_BANNED.equalsIgnoreCase(user.getStatus());
    }

    private String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePortal(String portal) {
        String normalized = isBlank(portal) ? DEFAULT_PORTAL : portal.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "admin", "crm", "lms", "elearning", "lcms" -> normalized;
            default -> DEFAULT_PORTAL;
        };
    }

    private String maskEmail(String email) {
        String normalized = trimToNull(email);
        if (normalized == null) {
            return null;
        }
        int separator = normalized.indexOf('@');
        if (separator <= 1) {
            return "***";
        }
        return normalized.substring(0, 1) + "***" + normalized.substring(separator);
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String toIso(OffsetDateTime value) {
        return value == null ? null : value.toString();
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
