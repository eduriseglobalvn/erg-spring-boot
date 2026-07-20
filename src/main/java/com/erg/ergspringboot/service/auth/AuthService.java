package com.erg.ergspringboot.service.auth;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.ProfileResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.SessionDeviceResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.TokenResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse;
import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.model.auth.RefreshTokenHistory;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.RefreshTokenHistoryRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;

@Service
public class AuthService {

    private static final String DEFAULT_TENANT = "erg";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String PROVIDER_LOCAL = "local";
    private static final String STATUS_BANNED = "BANNED";
    private static final String DEFAULT_PORTAL = "lms";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration PRIVILEGED_SESSION_TTL = Duration.ofHours(8);
    private static final Duration STANDARD_SESSION_TTL = Duration.ofHours(12);
    private static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final int LOGIN_ATTEMPT_THRESHOLD = 5;
    private static final List<String> FAILED_LOGIN_RESULTS = List.of("FAILED", "FAILURE");

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final AuthLoginAttemptRepository loginAttemptRepository;
    private final PasswordService passwordService;
    private final AuthTokenService tokenService;
    private SecurityEventRepository securityEventRepository;
    private LoginFailureEnforcementService loginFailureEnforcementService;
    private AuthLoginRateLimiter authLoginRateLimiter;
    private UserRoleAssignmentRepository userRoleAssignmentRepository;
    private RefreshTokenHistoryRepository refreshTokenHistoryRepository;
    @Value("${erg.auth.rapid-session-replacement.threshold:5}")
    private int rapidSessionReplacementThreshold = 5;
    @Value("${erg.auth.rapid-session-replacement.window-minutes:10}")
    private long rapidSessionReplacementWindowMinutes = 10;

    public AuthService(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            AuthLoginAttemptRepository loginAttemptRepository,
            PasswordService passwordService,
            AuthTokenService tokenService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
    }

    @Autowired(required = false)
    void setSecurityEventRepository(SecurityEventRepository securityEventRepository) {
        this.securityEventRepository = securityEventRepository;
    }

    @Autowired(required = false)
    void setLoginFailureEnforcementService(LoginFailureEnforcementService loginFailureEnforcementService) {
        this.loginFailureEnforcementService = loginFailureEnforcementService;
    }

    @Autowired(required = false)
    void setAuthLoginRateLimiter(AuthLoginRateLimiter authLoginRateLimiter) {
        this.authLoginRateLimiter = authLoginRateLimiter;
    }

    @Autowired(required = false)
    void setUserRoleAssignmentRepository(UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Autowired(required = false)
    void setRefreshTokenHistoryRepository(RefreshTokenHistoryRepository repository) {
        this.refreshTokenHistoryRepository = repository;
    }

    @Transactional
    public AuthResponse login(LoginRequest request, AuthClientContext context) {
        String identifier = normalizeIdentifier(request.identifier());
        String portal = normalizePortal(request.portal());
        if (authLoginRateLimiter != null && !authLoginRateLimiter.allow(identifier, context).allowed()) {
            throw new AuthFailure.TooManyAttempts();
        }
        User user = findUserByIdentifier(identifier)
                .orElseThrow(() -> {
                    passwordService.burnUnknownAccountCheck(request.password());
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
        if (STATUS_PENDING.equalsIgnoreCase(user.getStatus()) && PROVIDER_LOCAL.equalsIgnoreCase(user.getProvider())) {
            recordFailure(user, email, context, portal, "email_not_verified", false);
            throw new AuthFailure.EmailNotVerified();
        }
        if (!passwordService.matches(request.password(), user.getPasswordHash())) {
            recordFailure(user, email, context, portal, "invalid_credentials", true);
            throw new AuthFailure.InvalidCredentials();
        }

        if (restrictedFirstLogin) {
            user.setStatus("ACTIVE");
            userRepository.save(user);
        }

        return issueTokens(user, context, "erg", portal);
    }

    private String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }

    private java.util.Optional<User> findUserByIdentifier(String identifier) {
        return userRepository.findForLogin(identifier);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request, AuthClientContext context) {
        String currentHash = tokenService.hashToken(request.refreshToken());
        AuthSession session = sessionRepository.lockByRefreshTokenHash(currentHash)
                .filter(this::isSessionActive)
                .orElse(null);
        if (session == null) {
            AuthSession replayed = sessionRepository.findByPreviousRefreshTokenHash(currentHash).orElse(null);
            if (replayed == null && refreshTokenHistoryRepository != null) {
                replayed = refreshTokenHistoryRepository.findByTokenHash(currentHash)
                        .flatMap(history -> sessionRepository.findBySessionIdAndTenantId(
                                history.getSessionId(), history.getTenantId()))
                        .orElse(null);
            }
            if (replayed != null) {
                handleRefreshTokenReplay(replayed, context);
            }
            throw new AuthFailure.InvalidToken();
        }

        String accessToken = tokenService.newToken();
        String refreshToken = tokenService.newToken();
        OffsetDateTime now = now();
        if (refreshTokenHistoryRepository != null) {
            RefreshTokenHistory history = new RefreshTokenHistory();
            history.setId(IdGenerator.newId());
            history.setTokenHash(currentHash);
            history.setSessionId(session.getSessionId());
            history.setUserId(session.getUserId());
            history.setTenantId(firstNonBlank(session.getTenantId(), DEFAULT_TENANT));
            history.setConsumedAt(now);
            history.setExpiresAt(session.getExpiresAt());
            refreshTokenHistoryRepository.save(history);
        }
        session.setPreviousRefreshTokenHash(currentHash);
        session.setRefreshTokenRotatedAt(now);
        session.setAccessTokenHash(tokenService.hashToken(accessToken));
        session.setRefreshTokenHash(tokenService.hashToken(refreshToken));
        session.setLastActiveAt(now);
        sessionRepository.save(session);

        return new TokenResponse(accessToken, refreshToken, ACCESS_TOKEN_TTL.toSeconds(), "Bearer");
    }

    private void handleRefreshTokenReplay(AuthSession replayed, AuthClientContext context) {
        OffsetDateTime now = now();
        List<AuthSession> sessions = activeAccountSessions(replayed.getUserId(),
                firstNonBlank(replayed.getTenantId(), DEFAULT_TENANT));
        sessions.forEach(item -> {
            item.setRevokedAt(now);
            item.setRevokedReason("refresh_token_reuse");
        });
        if (!sessions.isEmpty()) sessionRepository.saveAll(sessions);
        if (securityEventRepository != null) {
            SecurityEvent event = new SecurityEvent();
            event.setId(IdGenerator.newId());
            event.setTenantId(replayed.getTenantId());
            event.setUserId(replayed.getUserId());
            event.setSessionId(replayed.getSessionId());
            event.setEventType("REFRESH_TOKEN_REUSE");
            event.setSeverity("CRITICAL");
            event.setRiskCode("TOKEN_REPLAY");
            event.setResult("SESSIONS_REVOKED");
            event.setReason("rotated_refresh_token_reused");
            event.setIpAddress(context == null ? null : context.ipAddress());
            event.setDeviceId(context == null ? null : context.deviceId());
            event.setOccurredAt(now);
            securityEventRepository.save(event);
        }
    }

    @Transactional
    public void logout(String userId, String tenantId, String sessionId) {
        AuthSession session = sessionRepository.findBySessionIdAndUserIdAndTenantId(sessionId, userId, tenantId)
                .orElseThrow(AuthFailure.InvalidToken::new);
        session.setRevokedAt(now());
        session.setRevokedReason("logout");
        sessionRepository.save(session);
    }

    @Transactional
    public int logoutAll(String userId, String tenantId) {
        List<AuthSession> sessions = activeAccountSessions(userId, firstNonBlank(tenantId, DEFAULT_TENANT));
        revokeSessions(sessions, now(), "logout_all");
        return sessions.size();
    }

    @Transactional(readOnly = true)
    public List<LoginAttemptResponse> listLoginAttempts(String userId, String email) {
        org.springframework.data.domain.PageRequest limit = org.springframework.data.domain.PageRequest.of(0, 100);
        List<AuthLoginAttempt> attempts;
        if (!isBlank(userId)) {
            attempts = loginAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId, limit);
        } else if (!isBlank(email)) {
            attempts = loginAttemptRepository.findByAttemptedEmailIgnoreCaseOrderByCreatedAtDesc(
                    normalizeEmail(email), limit);
        } else {
            attempts = List.of();
        }
        return attempts.stream()
                .map(this::loginAttemptResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public IpSecurityStatusResponse ipSecurityStatus(String email, AuthClientContext context) {
        String ip = context.ipAddress();
        long failedAttemptsIp = isBlank(ip)
                ? 0
                : loginAttemptRepository.countByIpAddressAndResultIn(ip, FAILED_LOGIN_RESULTS);
        Long failedAttemptsEmail = null;
        if (!isBlank(email)) {
            failedAttemptsEmail = loginAttemptRepository.countByAttemptedEmailIgnoreCaseAndResultIn(
                    normalizeEmail(email),
                    FAILED_LOGIN_RESULTS
            );
        }
        OffsetDateTime since = now().minus(LOGIN_ATTEMPT_WINDOW);
        long recentFailedAttemptsIp = isBlank(ip)
                ? 0
                : loginAttemptRepository.countByIpAddressAndResultInAndCreatedAtAfter(ip, FAILED_LOGIN_RESULTS, since);
        Long recentFailedAttemptsEmail = null;
        if (!isBlank(email)) {
            recentFailedAttemptsEmail = loginAttemptRepository.countByAttemptedEmailIgnoreCaseAndResultInAndCreatedAtAfter(
                    normalizeEmail(email),
                    FAILED_LOGIN_RESULTS,
                    since
            );
        }
        boolean blocked = recentFailedAttemptsIp >= LOGIN_ATTEMPT_THRESHOLD
                || (recentFailedAttemptsEmail != null && recentFailedAttemptsEmail >= LOGIN_ATTEMPT_THRESHOLD);

        return new IpSecurityStatusResponse(
                ip,
                blocked,
                false,
                null,
                null,
                false,
                failedAttemptsIp,
                failedAttemptsEmail,
                LOGIN_ATTEMPT_WINDOW.toSeconds(),
                LOGIN_ATTEMPT_THRESHOLD
        );
    }

    private void recordLoginAttempt(String userId, String email, AuthClientContext context, String portal, String result, String reason) {
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
        loginAttemptRepository.save(attempt);
    }

    private void recordFailure(
            User user,
            String email,
            AuthClientContext context,
            String portal,
            String reason,
        boolean enforceThreshold
    ) {
        if (loginFailureEnforcementService != null) {
            if (enforceThreshold && "invalid_credentials".equals(reason)) {
                loginFailureEnforcementService.recordInvalidCredentials(user, email, context, portal);
            } else {
                loginFailureEnforcementService.recordFailure(user, email, context, portal, reason, enforceThreshold);
            }
            return;
        }
        recordLoginAttempt(user == null ? null : user.getId(), email, context, portal, "FAILED", reason);
        if (enforceThreshold && user != null) {
            blockUserIfFailedLoginThresholdReached(user, email, context);
        }
    }

    private void blockUserIfFailedLoginThresholdReached(User user, String email, AuthClientContext context) {
        OffsetDateTime since = now().minus(LOGIN_ATTEMPT_WINDOW);
        boolean emailExceeded = loginAttemptRepository.countByAttemptedEmailIgnoreCaseAndResultInAndCreatedAtAfter(
                email,
                FAILED_LOGIN_RESULTS,
                since
        ) >= LOGIN_ATTEMPT_THRESHOLD;
        boolean ipExceeded = !isBlank(context.ipAddress())
                && loginAttemptRepository.countByIpAddressAndResultInAndCreatedAtAfter(
                context.ipAddress(),
                FAILED_LOGIN_RESULTS,
                since
        ) >= LOGIN_ATTEMPT_THRESHOLD;
        if (!emailExceeded && !ipExceeded) {
            return;
        }
        // Never mutate durable account state from attacker-controlled failures.
        // The distributed abuse limiter owns temporary throttling/challenges.
    }

    private SessionPlatform sessionPlatform(String deviceId, String deviceName, String userAgent) {
        String value = String.join(" ",
                firstNonBlank(deviceId, ""),
                firstNonBlank(deviceName, ""),
                firstNonBlank(userAgent, "")
        ).toLowerCase(Locale.ROOT);
        return value.matches(".*(mobile|android|iphone|ipad|tablet|pwa).*\\b.*")
                ? SessionPlatform.WEB_MOBILE
                : SessionPlatform.WEB_DESKTOP;
    }

    private SessionPlatform sessionPlatform(AuthSession session) {
        if (!isBlank(session.getPlatform())) {
            try {
                return SessionPlatform.valueOf(session.getPlatform().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Legacy or malformed values are classified from stored request metadata.
            }
        }
        return sessionPlatform(session.getDeviceId(), session.getDeviceName(), session.getUserAgent());
    }

    private boolean matchesBrowserContext(AuthSession session, AuthClientContext context) {
        if (!sameKnownValue(session.getDeviceId(), context.deviceId())) return false;
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
        return normalizedLeft != null && normalizedRight != null && normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    private enum SessionPlatform {
        WEB_DESKTOP,
        WEB_MOBILE
    }

    private List<AuthSession> activeAccountSessions(String userId, String tenantId) {
        List<AuthSession> sessions = sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull(userId, tenantId);
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        return sessions.stream().filter(this::isSessionActive).toList();
    }

    private void revokeSessions(List<AuthSession> sessions, OffsetDateTime revokedAt, String reason) {
        if (sessions.isEmpty()) {
            return;
        }
        sessions.forEach(session -> {
            session.setRevokedAt(revokedAt);
            session.setRevokedReason(reason);
        });
        sessionRepository.saveAll(sessions);
    }

    private AuthResponse issueTokens(User user, AuthClientContext context, String loginProvider, String portal) {
        List<String> permissions = effectivePermissions(user);
        List<String> portals = entitledPortals(user, permissions, portal);
        if (!portals.contains("*") && !portals.contains(portal)) {
            recordLoginAttempt(user.getId(), user.getEmail(), context, portal, "DENIED", "portal_forbidden");
            recordSecurityEvent(user, context, portal, "PORTAL_FORBIDDEN", "MEDIUM", "portal entitlement denied");
            throw new AuthFailure.PortalForbidden();
        }
        String accessToken = tokenService.newToken();
        String refreshToken = tokenService.newToken();
        OffsetDateTime now = now();
        String tenantId = firstNonBlank(user.getTenantId(), DEFAULT_TENANT);
        lockUserForSessionMutation(user.getId());
        List<AuthSession> activeAccountSessions = activeAccountSessions(user.getId(), tenantId);
        SessionPlatform platform = sessionPlatform(context.deviceId(), context.deviceName(), context.userAgent());
        boolean knownBrowserContext = activeAccountSessions.stream()
                .anyMatch(session -> matchesBrowserContext(session, context));
        boolean knownIpAddress = activeAccountSessions.stream()
                .anyMatch(session -> sameKnownValue(session.getIpAddress(), context.ipAddress()));
        List<AuthSession> replacedSessions = knownBrowserContext || knownIpAddress
                ? List.of()
                : activeAccountSessions;
        revokeSessions(replacedSessions, now, "session_replaced");
        if (!replacedSessions.isEmpty()) {
            recordSecurityEvent(
                    user,
                    context,
                    portal,
                    "SESSION_REPLACED",
                    "MEDIUM",
                    "active sessions replaced by a new browser context from an unknown IP"
            );
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
        session.setAccessTokenHash(tokenService.hashToken(accessToken));
        session.setRefreshTokenHash(tokenService.hashToken(refreshToken));
        session.setTenantId(tenantId);
        session.setLastActiveAt(now);
        session.setExpiresAt(now.plus(sessionTtl(user)));
        sessionRepository.save(session);
        recordLoginAttempt(user.getId(), user.getEmail(), context, portal, "SUCCESS", "login_success");

        user.setLastLoginProvider(loginProvider);
        user.setLastLoginAt(now);
        user.setLoginCount(user.getLoginCount() == null ? 1L : user.getLoginCount() + 1);
        userRepository.save(user);

        return new AuthResponse(
                profile(user),
                accessToken,
                refreshToken,
                ACCESS_TOKEN_TTL.toSeconds(),
                "Bearer",
                sessionResponse(session),
                permissions,
                portals,
                firstNonBlank(user.getAccountType(), "erg"),
                "user"
        );
    }

    private void lockUserForSessionMutation(String userId) {
        try {
            if (userRepository.lockIdForSessionMutation(userId).isEmpty()) {
                throw new AuthFailure.InvalidCredentials();
            }
        } catch (QueryTimeoutException | PessimisticLockingFailureException exception) {
            throw new AuthFailure.LoginInProgress();
        }
    }

    private List<String> effectivePermissions(User user) {
        Set<String> permissions = new TreeSet<>();
        if (user.getRoles() != null) {
            user.getRoles().stream()
                    .filter(java.util.Objects::nonNull)
                    .flatMap(role -> role.getPermissions() == null ? java.util.stream.Stream.empty() : role.getPermissions().stream())
                    .map(permission -> permission == null ? null : trimToNull(permission.getName()))
                    .filter(java.util.Objects::nonNull)
                    .forEach(permissions::add);
        }
        if (userRoleAssignmentRepository != null && user.getId() != null) {
            userRoleAssignmentRepository.findActiveForUser(
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
                        if (permission == null) return;
                        if ("deny".equalsIgnoreCase(override.getGrantType())) permissions.remove(permission);
                        else if ("grant".equalsIgnoreCase(override.getGrantType())) permissions.add(permission);
                    });
        }
        return List.copyOf(permissions);
    }

    private List<String> entitledPortals(User user, List<String> permissions, String requestedPortal) {
        if (permissions.contains("*")) return List.of("*");

        Set<String> portals = new TreeSet<>();
        permissions.forEach(permission -> {
            int separator = permission.indexOf('.');
            if (separator <= 0) return;
            String namespace = permission.substring(0, separator).toLowerCase(Locale.ROOT);
            if (Set.of("admin", "crm", "lcms", "lms", "elearning").contains(namespace)) portals.add(namespace);
        });
        if (portals.isEmpty()) portals.add(requestedPortal);
        return List.copyOf(portals);
    }

    private Duration sessionTtl(User user) {
        boolean privileged = user.getRoles() != null && user.getRoles().stream()
                .map(Role::getName)
                .filter(java.util.Objects::nonNull)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.contains("admin") || name.contains("manager") || name.contains("coordinator"));
        return privileged ? PRIVILEGED_SESSION_TTL : STANDARD_SESSION_TTL;
    }

    private void recordSecurityEvent(
            User user,
            AuthClientContext context,
            String portal,
            String eventType,
            String severity,
            String reason
    ) {
        if (securityEventRepository == null) {
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
        event.setPlatform(sessionPlatform(context.deviceId(), context.deviceName(), context.userAgent()).name());
        event.setResult("RAPID_SESSION_REPLACEMENT".equals(eventType) ? "WARNING"
                : eventType.endsWith("FORBIDDEN") ? "DENIED" : "SUCCESS");
        event.setRiskCode("SESSION_REPLACED".equals(eventType) || "RAPID_SESSION_REPLACEMENT".equals(eventType)
                || eventType.endsWith("FORBIDDEN") ? eventType : null);
        event.setMetadata(java.util.Map.of(
                "deviceName", firstNonBlank(context.deviceName(), "Browser"),
                "portal", portal
        ));
        event.setOccurredAt(now());
        securityEventRepository.save(event);
    }

    private void recordRapidSessionReplacementWarningIfRequired(
            User user,
            AuthClientContext context,
            String portal
    ) {
        if (securityEventRepository == null) {
            return;
        }
        String tenantId = firstNonBlank(user.getTenantId(), DEFAULT_TENANT);
        long replacementCount = securityEventRepository
                .countByTenantIdAndUserIdAndEventTypeAndOccurredAtAfter(
                        tenantId,
                        user.getId(),
                        "SESSION_REPLACED",
                        now().minusMinutes(rapidSessionReplacementWindowMinutes)
                );
        if (replacementCount < rapidSessionReplacementThreshold) {
            return;
        }
        recordSecurityEvent(
                user,
                context,
                portal,
                "RAPID_SESSION_REPLACEMENT",
                "HIGH",
                "session replacement threshold reached; warning only"
        );
    }

    private ProfileResponse profile(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getStatus(),
                user.getProvider(),
                firstNonBlank(user.getAccountType(), "erg"),
                "user",
                  user.getLastLoginProvider(),
                  roles(user.getRoles()),
                  Boolean.TRUE.equals(user.getIsProfileCompleted()),
                  toIso(user.getCreatedAt()),
                  lifecycle(user)
          );
      }

    private AccountLifecycleResponse lifecycle(User user) {
        List<String> nextSteps = new java.util.ArrayList<>();
        if (!Boolean.TRUE.equals(user.getIsProfileCompleted())) nextSteps.add("COMPLETE_PROFILE");
        if (!Boolean.TRUE.equals(user.getRecoveryEmailVerified())) nextSteps.add("VERIFY_RECOVERY_EMAIL");
        if (Boolean.TRUE.equals(user.getMustChangePassword())) nextSteps.add("CHANGE_PASSWORD");
        String lifecycleStatus = nextSteps.isEmpty()
                ? "ACTIVE"
                : ("INVITED".equalsIgnoreCase(user.getStatus()) ? "PROVISIONED" : "FIRST_LOGIN_RESTRICTED");
        return new AccountLifecycleResponse(
                lifecycleStatus,
                Boolean.TRUE.equals(user.getIsProfileCompleted()),
                maskEmail(user.getRecoveryEmail()),
                Boolean.TRUE.equals(user.getRecoveryEmailVerified()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                List.copyOf(nextSteps)
        );
    }

    private String maskEmail(String email) {
        String normalized = trimToNull(email);
        if (normalized == null) return null;
        int separator = normalized.indexOf('@');
        if (separator <= 1) return "***";
        return normalized.substring(0, 1) + "***" + normalized.substring(separator);
    }

    private SessionDeviceResponse sessionResponse(AuthSession session) {
        return new SessionDeviceResponse(
                session.getSessionId(),
                session.getDeviceId(),
                session.getDeviceName(),
                session.getPortal(),
                session.getIpAddress(),
                session.getUserAgent(),
                true,
                session.getRevokedAt() != null,
                session.getRevokedReason(),
                toIso(session.getCreatedAt()),
                toIso(session.getLastActiveAt()),
                toIso(session.getExpiresAt())
        );
    }

    private LoginAttemptResponse loginAttemptResponse(AuthLoginAttempt attempt) {
        return new LoginAttemptResponse(
                attempt.getId(),
                attempt.getTenantId(),
                attempt.getUserId(),
                attempt.getAttemptedEmail(),
                attempt.getIpAddress(),
                attempt.getCountryCode(),
                attempt.getCountryName(),
                attempt.getContinentCode(),
                attempt.getUserAgent(),
                attempt.getDeviceId(),
                attempt.getDeviceName(),
                attempt.getPortal(),
                attempt.getResult(),
                attempt.getReason(),
                toIso(attempt.getCreatedAt())
        );
    }

    private List<String> roles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("user");
        }
        return roles.stream()
                .map(Role::getName)
                .filter(name -> !isBlank(name))
                .collect(Collectors.toList());
    }

    private boolean isSessionActive(AuthSession session) {
        return session.getRevokedAt() == null
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(now());
    }

    private boolean isLocked(User user) {
        return STATUS_BLOCKED.equalsIgnoreCase(user.getStatus()) || STATUS_BANNED.equalsIgnoreCase(user.getStatus());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String toIso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String firstNonBlank(String first, String second, String third, String fallback) {
        if (!isBlank(first)) return first.trim();
        if (!isBlank(second)) return second.trim();
        if (!isBlank(third)) return third.trim();
        return fallback;
    }

    private String normalizePortal(String portal) {
        String normalized = isBlank(portal) ? DEFAULT_PORTAL : portal.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "admin", "crm", "lms", "elearning", "lcms" -> normalized;
            default -> DEFAULT_PORTAL;
        };
    }

    private boolean isProfileComplete(String fullName, String phone) {
        return !isBlank(fullName) && !isBlank(phone);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
