package com.erg.ergspringboot.service.auth;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.ForgotPasswordRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.GoogleLoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RegisterRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.ResendPinRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.ResetPasswordRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.SocialLoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.VerifyPinRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.ProfileResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.SessionDeviceResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.TokenResponse;
import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.model.auth.AuthPin;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.users.SocialAccount;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthPinRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.SocialAccountRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String DEFAULT_TENANT = "erg";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String PROVIDER_LOCAL = "local";
    private static final String PROVIDER_GOOGLE = "google";
    private static final String STATUS_BANNED = "BANNED";
    private static final String DEFAULT_PORTAL = "lms";
    private static final int MAX_ACTIVE_DEVICE_SESSIONS = 2;
    private static final String PIN_PURPOSE_REGISTER = "register";
    private static final String PIN_PURPOSE_VERIFY = "verify";
    private static final String PIN_PURPOSE_FORGOT_PASSWORD = "forgot_password";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
    private static final Duration PIN_TTL = Duration.ofMinutes(15);
    private static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final int LOGIN_ATTEMPT_THRESHOLD = 5;
    private static final List<String> FAILED_LOGIN_RESULTS = List.of("FAILED", "FAILURE");

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final AuthPinRepository pinRepository;
    private final AuthLoginAttemptRepository loginAttemptRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordService passwordService;
    private final AuthTokenService tokenService;

    @Value("${erg.auth.oauth.google.client-id:}")
    private String googleOauthClientId;

    @Value("${erg.auth.oauth.google.authorization-uri:https://accounts.google.com/o/oauth2/v2/auth}")
    private String googleAuthorizationUri;

    @Value("${erg.auth.oauth.facebook.client-id:}")
    private String facebookOauthClientId;

    @Value("${erg.auth.oauth.facebook.authorization-uri:https://www.facebook.com/v19.0/dialog/oauth}")
    private String facebookAuthorizationUri;

    public AuthService(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            AuthPinRepository pinRepository,
            AuthLoginAttemptRepository loginAttemptRepository,
            SocialAccountRepository socialAccountRepository,
            PasswordService passwordService,
            AuthTokenService tokenService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.pinRepository = pinRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AuthFailure.EmailExists();
        }

        User user = new User();
        user.setId(IdGenerator.newId());
        user.setEmail(email);
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setFullName(trimToNull(request.fullName()));
        user.setStatus(STATUS_PENDING);
        user.setProvider(PROVIDER_LOCAL);
        user.setAccountType("erg");
        user.setLastLoginProvider(PROVIDER_LOCAL);
        user.setTenantId(DEFAULT_TENANT);
        user.setIsProfileCompleted(false);
        user.setLoginCount(0L);
        userRepository.save(user);

        pinRepository.save(newPin(email, PIN_PURPOSE_REGISTER));
    }

    @Transactional
    public AuthResponse login(LoginRequest request, AuthClientContext context) {
        String email = normalizeEmail(request.email());
        String portal = normalizePortal(request.portal());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    recordLoginAttempt(null, email, context, portal, "FAILED", "invalid_credentials");
                    return new AuthFailure.InvalidCredentials();
                });

        if (isLocked(user)) {
            recordLoginAttempt(user.getId(), email, context, portal, "FAILED", "account_locked");
            throw new AuthFailure.AccountLocked();
        }
        if (STATUS_PENDING.equalsIgnoreCase(user.getStatus()) && PROVIDER_LOCAL.equalsIgnoreCase(user.getProvider())) {
            recordLoginAttempt(user.getId(), email, context, portal, "FAILED", "email_not_verified");
            throw new AuthFailure.EmailNotVerified();
        }
        if (!passwordService.matches(request.password(), user.getPasswordHash())) {
            recordLoginAttempt(user.getId(), email, context, portal, "FAILED", "invalid_credentials");
            throw new AuthFailure.InvalidCredentials();
        }

        return issueTokens(user, context, "erg", portal);
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public TokenResponse refresh(RefreshRequest request, AuthClientContext context) {
        String currentHash = tokenService.hashToken(request.refreshToken());
        AuthSession session = sessionRepository.findByRefreshTokenHash(currentHash)
                .filter(this::isSessionActive)
                .orElseThrow(AuthFailure.InvalidToken::new);

        String accessToken = tokenService.newToken();
        String refreshToken = tokenService.newToken();
        OffsetDateTime now = now();
        session.setAccessTokenHash(tokenService.hashToken(accessToken));
        session.setRefreshTokenHash(tokenService.hashToken(refreshToken));
        session.setLastActiveAt(now);
        session.setExpiresAt(now.plus(REFRESH_TOKEN_TTL));
        sessionRepository.save(session);

        return new TokenResponse(accessToken, refreshToken, ACCESS_TOKEN_TTL.toSeconds(), "Bearer");
    }

    @Transactional
    public void verifyPin(VerifyPinRequest request) {
        String email = normalizeEmail(request.email());
        String purpose = normalizePurpose(request.purpose());
        AuthPin pin = consumePin(email, request.code(), purpose);

        if (PIN_PURPOSE_REGISTER.equals(purpose) || PIN_PURPOSE_VERIFY.equals(purpose)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
                user.setStatus(STATUS_ACTIVE);
                userRepository.save(user);
            });
        }
        pinRepository.save(pin);
    }

    @Transactional
    public void resendPin(ResendPinRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            return;
        }
        pinRepository.save(newPin(email, normalizePurpose(request.purpose())));
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            return;
        }
        pinRepository.save(newPin(email, PIN_PURPOSE_FORGOT_PASSWORD));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.email());
        AuthPin pin = consumePin(email, request.code(), PIN_PURPOSE_FORGOT_PASSWORD);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(AuthFailure.InvalidCredentials::new);
        user.setPasswordHash(passwordService.hash(request.newPassword()));
        userRepository.save(user);
        pinRepository.save(pin);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request, AuthClientContext context) {
        if (!Boolean.TRUE.equals(request.emailVerified()) || isBlank(request.googleSub())) {
            throw new AuthFailure.GoogleIdentityInvalid();
        }

        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User created = new User();
            created.setId(IdGenerator.newId());
            created.setEmail(email);
            created.setPasswordHash(passwordService.hash(tokenService.newToken()));
            created.setStatus(STATUS_ACTIVE);
            created.setTenantId(DEFAULT_TENANT);
            created.setLoginCount(0L);
            return created;
        });

        user.setFullName(firstNonBlank(request.fullName(), user.getFullName()));
        user.setAvatarUrl(firstNonBlank(request.avatarUrl(), user.getAvatarUrl()));
        user.setProvider(PROVIDER_GOOGLE);
        user.setProviderId(request.googleSub());
        user.setAccountType(PROVIDER_GOOGLE);
        user.setGoogleSub(request.googleSub());
        user.setGoogleEmail(email);
        user.setGoogleEmailVerified(true);
        user.setLastLoginProvider(PROVIDER_GOOGLE);
        user.setIsProfileCompleted(Boolean.TRUE.equals(user.getIsProfileCompleted()));
        userRepository.save(user);
        upsertSocialAccount(PROVIDER_GOOGLE, user, request.googleSub(), email, null, null, null);

        return issueTokens(user, context, PROVIDER_GOOGLE, normalizePortal(request.portal()));
    }

    public String buildOauthAuthorizationUrl(String provider, String state, String redirectUri) {
        String normalizedProvider = normalizeProvider(provider);
        String clientId = oauthClientId(normalizedProvider);
        if (isBlank(clientId) || isBlank(redirectUri)) {
            throw new AuthFailure.OAuthNotConfigured();
        }
        String authorizationUri = PROVIDER_GOOGLE.equals(normalizedProvider)
                ? googleAuthorizationUri
                : facebookAuthorizationUri;
        String scope = PROVIDER_GOOGLE.equals(normalizedProvider) ? "openid email profile" : "email public_profile";
        return authorizationUri
                + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode(scope)
                + (isBlank(state) ? "" : "&state=" + encode(state));
    }

    public AuthResponse oauthCallback(String provider, String code, String state, AuthClientContext context) {
        if (isBlank(code) || isBlank(oauthClientId(normalizeProvider(provider)))) {
            throw new AuthFailure.OAuthNotConfigured();
        }
        throw new AuthFailure.OAuthNotConfigured();
    }

    @Transactional
    public AuthResponse socialLogin(String provider, SocialLoginRequest request, AuthClientContext context) {
        String normalizedProvider = normalizeProvider(provider);
        if (!Boolean.TRUE.equals(request.emailVerified()) || isBlank(request.providerId())) {
            throw new AuthFailure.SocialIdentityInvalid();
        }
        String email = normalizeEmail(request.email());
        String providerId = request.providerId().trim();

        User user = socialAccountRepository.findByProviderAndProviderId(normalizedProvider, providerId)
                .flatMap(account -> userRepository.findById(account.getUserId()))
                .or(() -> userRepository.findByEmailIgnoreCase(email))
                .orElseGet(() -> newSocialUser(normalizedProvider, email, providerId, request));

        if (isLocked(user)) {
            throw new AuthFailure.AccountLocked();
        }

        user.setFullName(firstNonBlank(request.fullName(), user.getFullName()));
        user.setAvatarUrl(firstNonBlank(request.avatarUrl(), user.getAvatarUrl()));
        if (isBlank(user.getProvider())) {
            user.setProvider(normalizedProvider);
        }
        user.setProviderId(providerId);
        user.setAccountType(mergeAccountType(user.getAccountType(), user.getProvider(), providerId, normalizedProvider));
        user.setLastLoginProvider(normalizedProvider);
        user.setStatus(STATUS_ACTIVE);
        user.setIsProfileCompleted(isProfileComplete(user.getFullName(), user.getPhone()));
        if (PROVIDER_GOOGLE.equals(normalizedProvider)) {
            user.setGoogleSub(providerId);
            user.setGoogleEmail(email);
            user.setGoogleEmailVerified(true);
        }
        userRepository.save(user);
        upsertSocialAccount(
                normalizedProvider,
                user,
                providerId,
                email,
                request.accessToken(),
                request.refreshToken(),
                null
        );
        return issueTokens(user, context, normalizedProvider, normalizePortal(request.portal()));
    }

    @Transactional
    @CacheEvict(cacheNames = {"authPrincipal", "sessionBootstrap"}, allEntries = true)
    public void logout(String userId, String tenantId, String sessionId) {
        AuthSession session = sessionRepository.findBySessionIdAndUserIdAndTenantId(sessionId, userId, tenantId)
                .orElseThrow(AuthFailure.InvalidToken::new);
        session.setRevokedAt(now());
        session.setRevokedReason("logout");
        sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<LoginAttemptResponse> listLoginAttempts(String userId, String email) {
        List<AuthLoginAttempt> attempts;
        if (!isBlank(userId)) {
            attempts = loginAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId);
        } else if (!isBlank(email)) {
            attempts = loginAttemptRepository.findByAttemptedEmailIgnoreCaseOrderByCreatedAtDesc(normalizeEmail(email));
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
        boolean blocked = failedAttemptsIp >= LOGIN_ATTEMPT_THRESHOLD
                || (failedAttemptsEmail != null && failedAttemptsEmail >= LOGIN_ATTEMPT_THRESHOLD);

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

    private boolean wouldBecomeThirdActiveDevice(List<AuthSession> activeSessions, AuthClientContext context) {
        Set<String> devices = activeSessions.stream()
                .map(this::sessionDeviceKey)
                .collect(Collectors.toSet());
        devices.add(firstNonBlank(context.deviceId(), context.ipAddress(), context.userAgent(), "web"));
        return devices.size() > MAX_ACTIVE_DEVICE_SESSIONS;
    }

    private String sessionDeviceKey(AuthSession session) {
        return firstNonBlank(session.getDeviceId(), session.getIpAddress(), session.getUserAgent(), session.getSessionId());
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
        String accessToken = tokenService.newToken();
        String refreshToken = tokenService.newToken();
        OffsetDateTime now = now();
        String tenantId = firstNonBlank(user.getTenantId(), DEFAULT_TENANT);
        List<AuthSession> activePortalSessions = sessionRepository.findByUserIdAndTenantIdAndPortalAndRevokedAtIsNull(
                user.getId(),
                tenantId,
                portal
        );
        activePortalSessions = activePortalSessions == null ? List.of() : activePortalSessions;
        if (wouldBecomeThirdActiveDevice(activePortalSessions, context)) {
            user.setStatus(STATUS_BANNED);
            userRepository.save(user);
            revokeSessions(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull(user.getId(), tenantId), now, "auto_banned_three_devices");
            recordLoginAttempt(user.getId(), user.getEmail(), context, portal, "BANNED", "three_active_devices");
            throw new AuthFailure.AccountLocked();
        }

        revokeSessions(activePortalSessions, now, "replaced_by_new_login");

        AuthSession session = new AuthSession();
        session.setId(IdGenerator.newId());
        session.setUserId(user.getId());
        session.setSessionId(IdGenerator.newId());
        session.setDeviceId(firstNonBlank(context.deviceId(), "web"));
        session.setDeviceName(firstNonBlank(context.deviceName(), "Browser"));
        session.setPortal(portal);
        session.setIpAddress(context.ipAddress());
        session.setUserAgent(context.userAgent());
        session.setAccessTokenHash(tokenService.hashToken(accessToken));
        session.setRefreshTokenHash(tokenService.hashToken(refreshToken));
        session.setTenantId(tenantId);
        session.setLastActiveAt(now);
        session.setExpiresAt(now.plus(REFRESH_TOKEN_TTL));
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
                List.of(),
                List.of(portal),
                firstNonBlank(user.getAccountType(), "erg"),
                "user"
        );
    }

    private User newSocialUser(String provider, String email, String providerId, SocialLoginRequest request) {
        User created = new User();
        created.setId(IdGenerator.newId());
        created.setEmail(email);
        created.setPasswordHash(passwordService.hash(tokenService.newToken()));
        created.setFullName(trimToNull(request.fullName()));
        created.setAvatarUrl(trimToNull(request.avatarUrl()));
        created.setStatus(STATUS_ACTIVE);
        created.setProvider(provider);
        created.setProviderId(providerId);
        created.setAccountType(provider);
        created.setTenantId(DEFAULT_TENANT);
        created.setLoginCount(0L);
        created.setIsProfileCompleted(isProfileComplete(created.getFullName(), created.getPhone()));
        return created;
    }

    private void upsertSocialAccount(
            String provider,
            User user,
            String providerId,
            String email,
            String accessToken,
            String refreshToken,
            OffsetDateTime expiresAt
    ) {
        SocialAccount account = socialAccountRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    SocialAccount created = new SocialAccount();
                    created.setId(IdGenerator.newId());
                    created.setProvider(provider);
                    created.setProviderId(providerId);
                    return created;
                });
        account.setUserId(user.getId());
        account.setEmail(email);
        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        account.setExpiresAt(expiresAt);
        socialAccountRepository.save(account);
    }

    private AuthPin consumePin(String email, String code, String purpose) {
        AuthPin pin = pinRepository
                .findFirstByEmailIgnoreCaseAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(AuthFailure.InvalidPin::new);
        if (!code.equals(pin.getCode()) || pin.getExpiresAt() == null || pin.getExpiresAt().isBefore(now())) {
            throw new AuthFailure.InvalidPin();
        }
        pin.setUsedAt(now());
        return pin;
    }

    private AuthPin newPin(String email, String purpose) {
        OffsetDateTime createdAt = now();
        AuthPin pin = new AuthPin();
        pin.setId(IdGenerator.newId());
        pin.setEmail(email);
        pin.setCode(generatePin());
        pin.setPurpose(purpose);
        pin.setCreatedAt(createdAt);
        pin.setExpiresAt(createdAt.plus(PIN_TTL));
        return pin;
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
                user.getGoogleSub(),
                user.getGoogleEmail(),
                user.getGoogleEmailVerified(),
                user.getLastLoginProvider(),
                roles(user.getRoles()),
                Boolean.TRUE.equals(user.getIsProfileCompleted()),
                toIso(user.getCreatedAt())
        );
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
        return "BLOCKED".equalsIgnoreCase(user.getStatus()) || "BANNED".equalsIgnoreCase(user.getStatus());
    }

    private String normalizeProvider(String provider) {
        String normalized = isBlank(provider) ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!List.of(PROVIDER_GOOGLE, "facebook", "apple").contains(normalized)) {
            throw new AuthFailure.SocialIdentityInvalid();
        }
        return normalized;
    }

    private String oauthClientId(String provider) {
        return PROVIDER_GOOGLE.equals(provider) ? googleOauthClientId : facebookOauthClientId;
    }

    private String normalizePurpose(String purpose) {
        if (isBlank(purpose)) {
            return PIN_PURPOSE_VERIFY;
        }
        return purpose.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generatePin() {
        int value = java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return "%06d".formatted(value);
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
            case "admin", "lms", "elearning", "lcms" -> normalized;
            default -> DEFAULT_PORTAL;
        };
    }

    private boolean isProfileComplete(String fullName, String phone) {
        return !isBlank(fullName) && !isBlank(phone);
    }

    private String mergeAccountType(String current, String provider, String providerId, String loginProvider) {
        String normalizedCurrent = isBlank(current) ? "" : current.trim().toLowerCase(Locale.ROOT);
        if ("hybrid".equals(normalizedCurrent)) {
            return "hybrid";
        }
        if ("erg".equals(normalizedCurrent) && !isBlank(providerId)) {
            return "hybrid";
        }
        if (!isBlank(providerId)) {
            return loginProvider;
        }
        return firstNonBlank(provider, "erg");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

