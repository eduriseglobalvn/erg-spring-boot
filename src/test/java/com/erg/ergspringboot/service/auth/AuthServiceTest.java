package com.erg.ergspringboot.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RegisterRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.SocialLoginRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.exception.auth.AuthFailure;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionRepository sessionRepository;

    @Mock
    private AuthPinRepository pinRepository;

    @Mock
    private AuthLoginAttemptRepository loginAttemptRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    private PasswordService passwordService;
    private AuthTokenService tokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        tokenService = new AuthTokenService();
        authService = new AuthService(
                userRepository,
                sessionRepository,
                pinRepository,
                loginAttemptRepository,
                socialAccountRepository,
                passwordService,
                tokenService
        );
    }

    @Test
    void registerCreatesPendingLocalUserAndVerificationPin() {
        when(userRepository.existsByEmailIgnoreCase("new.user@example.com")).thenReturn(false);

        authService.register(new RegisterRequest("New.User@Example.com", "secret123", "New User"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<AuthPin> pinCaptor = ArgumentCaptor.forClass(AuthPin.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        verify(pinRepository).save(pinCaptor.capture());

        User user = userCaptor.getValue();
        assertNotNull(user.getId());
        assertEquals("new.user@example.com", user.getEmail());
        assertEquals("New User", user.getFullName());
        assertEquals("PENDING", user.getStatus());
        assertEquals("local", user.getProvider());
        assertEquals("erg", user.getTenantId());
        assertNotEquals("secret123", user.getPasswordHash());
        assertTrue(passwordService.matches("secret123", user.getPasswordHash()));

        AuthPin pin = pinCaptor.getValue();
        assertNotNull(pin.getId());
        assertEquals("new.user@example.com", pin.getEmail());
        assertEquals("register", pin.getPurpose());
        assertEquals(6, pin.getCode().length());
        assertNotNull(pin.getExpiresAt());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThrows(
                AuthFailure.EmailExists.class,
                () -> authService.register(new RegisterRequest("Taken@Example.com", "secret123", "Taken User"))
        );
    }

    @Test
    void loginIssuesTokensAndStoresHashedRefreshTokenForActiveUser() {
        User user = activeUser("user-1", "active@example.com", "secret123");
        when(userRepository.findByEmailIgnoreCase("active@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(
                new LoginRequest("ACTIVE@example.com", "secret123", "web-1", "Chrome", "fingerprint", "lms", true),
                new AuthClientContext("127.0.0.1", "JUnit", "web-1", "Chrome", "fingerprint")
        );

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        verify(userRepository).save(user);

        AuthSession session = sessionCaptor.getValue();
        assertEquals("user-1", session.getUserId());
        assertEquals("web-1", session.getDeviceId());
        assertEquals("Chrome", session.getDeviceName());
        assertEquals("127.0.0.1", session.getIpAddress());
        assertNotEquals(response.refreshToken(), session.getRefreshTokenHash());

        assertEquals("active@example.com", response.user().email());
        assertEquals("Bearer", response.tokenType());
        assertFalse(response.accessToken().isBlank());
        assertFalse(response.refreshToken().isBlank());
        assertEquals(900, response.expiresIn());
        assertEquals(1L, user.getLoginCount());
        assertNotNull(user.getLastLoginAt());
    }

    @Test
    void loginRejectsPendingLocalUser() {
        User user = activeUser("user-1", "pending@example.com", "secret123");
        user.setStatus("PENDING");
        when(userRepository.findByEmailIgnoreCase("pending@example.com")).thenReturn(Optional.of(user));

        assertThrows(
                AuthFailure.EmailNotVerified.class,
                () -> authService.login(
                        new LoginRequest("pending@example.com", "secret123", null, null, null, "lms", false),
                        new AuthClientContext("127.0.0.1", "JUnit", null, null, null)
                )
        );
    }

    @Test
    void loginRejectsBannedUser() {
        User user = activeUser("user-1", "banned@example.com", "secret123");
        user.setStatus("BANNED");
        when(userRepository.findByEmailIgnoreCase("banned@example.com")).thenReturn(Optional.of(user));

        assertThrows(
                AuthFailure.AccountLocked.class,
                () -> authService.login(
                        new LoginRequest("banned@example.com", "secret123", null, null, null, "lms", false),
                        new AuthClientContext("127.0.0.1", "JUnit", null, null, null)
                )
        );
    }

    @Test
    void thirdActivePortalDeviceAutoBansAndRevokesSessions() {
        User user = activeUser("user-1", "active@example.com", "secret123");
        AuthSession existingWeb = session("session-1", "user-1", "erg");
        existingWeb.setDeviceId("web-1");
        AuthSession existingMobile = session("session-2", "user-1", "erg");
        existingMobile.setDeviceId("mobile-1");
        when(userRepository.findByEmailIgnoreCase("active@example.com")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdAndPortalAndRevokedAtIsNull("user-1", "erg", "lms"))
                .thenReturn(List.of(existingWeb, existingMobile));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "erg"))
                .thenReturn(List.of(existingWeb, existingMobile));

        assertThrows(
                AuthFailure.AccountLocked.class,
                () -> authService.login(
                        new LoginRequest("active@example.com", "secret123", "tablet-1", "Tablet", null, "lms", false),
                        new AuthClientContext("127.0.0.1", "JUnit", "tablet-1", "Tablet", null)
                )
        );

        assertEquals("BANNED", user.getStatus());
        assertEquals("auto_banned_three_devices", existingWeb.getRevokedReason());
        assertEquals("auto_banned_three_devices", existingMobile.getRevokedReason());
        verify(userRepository).save(user);
        verify(sessionRepository).saveAll(List.of(existingWeb, existingMobile));
    }

    @Test
    void logoutRevokesCurrentSession() {
        AuthSession session = session("session-1", "user-1", "erg");
        when(sessionRepository.findBySessionIdAndUserIdAndTenantId("session-1", "user-1", "erg"))
                .thenReturn(Optional.of(session));

        authService.logout("user-1", "erg", "session-1");

        assertNotNull(session.getRevokedAt());
        assertEquals("logout", session.getRevokedReason());
        verify(sessionRepository).save(session);
    }

    @Test
    void listLoginAttemptsMapsUserHistoryNewestFirst() {
        AuthLoginAttempt attempt = loginAttempt("attempt-1", "user-1", "active@example.com", "FAILED");
        when(loginAttemptRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(attempt));

        List<LoginAttemptResponse> response = authService.listLoginAttempts("user-1", null);

        assertEquals(1, response.size());
        assertEquals("attempt-1", response.getFirst().id());
        assertEquals("active@example.com", response.getFirst().attemptedEmail());
        assertEquals("FAILED", response.getFirst().result());
    }

    @Test
    void ipSecurityStatusCountsFailedAttemptsForIpAndEmail() {
        when(loginAttemptRepository.countByIpAddressAndResultIn(eq("10.0.0.1"), any())).thenReturn(1L);
        when(loginAttemptRepository.countByAttemptedEmailIgnoreCaseAndResultIn(eq("active@example.com"), any())).thenReturn(1L);

        IpSecurityStatusResponse response = authService.ipSecurityStatus(
                "active@example.com",
                new AuthClientContext("10.0.0.1", "JUnit", "web-1", "Chrome", "fingerprint")
        );

        assertEquals("10.0.0.1", response.ip());
        assertFalse(response.blocked());
        assertFalse(response.allowlisted());
        assertEquals(1L, response.failedAttemptsIp());
        assertEquals(1L, response.failedAttemptsEmail());
        assertEquals(5, response.threshold());
    }

    @Test
    void socialLoginCreatesUserAndLinksSocialAccount() {
        when(userRepository.findByEmailIgnoreCase("apple@example.com")).thenReturn(Optional.empty());
        when(socialAccountRepository.findByProviderAndProviderId("apple", "apple-sub-1")).thenReturn(Optional.empty());

        AuthResponse response = authService.socialLogin(
                "apple",
                new SocialLoginRequest(
                        "id-token",
                        "apple@example.com",
                        "Apple User",
                        "",
                        "apple-sub-1",
                        true,
                        "ios-1",
                        "iPhone",
                        null,
                        "lms",
                        null,
                        null
                ),
                new AuthClientContext("127.0.0.1", "JUnit", "ios-1", "iPhone", null)
        );

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<SocialAccount> socialCaptor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        verify(socialAccountRepository).save(socialCaptor.capture());

        assertEquals("apple@example.com", response.user().email());
        assertEquals("apple", response.user().provider());
        assertEquals("ios-1", response.session().deviceId());
        assertEquals("apple", userCaptor.getValue().getProvider());
        assertEquals("apple-sub-1", socialCaptor.getValue().getProviderId());
    }

    @Test
    void buildOauthAuthorizationUrlRejectsUnconfiguredProvider() {
        assertThrows(
                AuthFailure.OAuthNotConfigured.class,
                () -> authService.buildOauthAuthorizationUrl("google", "state-1", "https://app.example.com/callback")
        );
    }

    private User activeUser(String id, String email, String rawPassword) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash(passwordService.hash(rawPassword));
        user.setFullName("Active User");
        user.setStatus("ACTIVE");
        user.setProvider("local");
        user.setAccountType("erg");
        user.setTenantId("erg");
        user.setIsProfileCompleted(false);
        user.setLoginCount(0L);
        return user;
    }

    private AuthSession session(String sessionId, String userId, String tenantId) {
        AuthSession session = new AuthSession();
        session.setId("id-" + sessionId);
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTenantId(tenantId);
        session.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        return session;
    }

    private AuthLoginAttempt loginAttempt(String id, String userId, String email, String result) {
        AuthLoginAttempt attempt = new AuthLoginAttempt();
        attempt.setId(id);
        attempt.setTenantId("erg");
        attempt.setUserId(userId);
        attempt.setAttemptedEmail(email);
        attempt.setIpAddress("127.0.0.1");
        attempt.setCountryCode("VN");
        attempt.setCountryName("Vietnam");
        attempt.setContinentCode("AS");
        attempt.setUserAgent("JUnit");
        attempt.setDeviceId("web-1");
        attempt.setDeviceName("Chrome");
        attempt.setResult(result);
        attempt.setReason("bad_password");
        attempt.setCreatedAt(OffsetDateTime.parse("2026-05-20T00:00:00Z"));
        return attempt;
    }
}

