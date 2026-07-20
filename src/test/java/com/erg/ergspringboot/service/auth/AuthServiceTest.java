package com.erg.ergspringboot.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.erg.ergspringboot.common.dto.auth.AuthClientContext;
import com.erg.ergspringboot.common.request.auth.AuthRequests.LoginRequest;
import com.erg.ergspringboot.common.request.auth.AuthRequests.RefreshRequest;
import com.erg.ergspringboot.common.response.auth.AuthResponses.AuthResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.IpSecurityStatusResponse;
import com.erg.ergspringboot.common.response.auth.AuthResponses.LoginAttemptResponse;
import com.erg.ergspringboot.exception.auth.AuthFailure;
import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.repository.auth.AuthLoginAttemptRepository;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.SecurityEventRepository;
import com.erg.ergspringboot.repository.auth.RefreshTokenHistoryRepository;
import com.erg.ergspringboot.model.auth.RefreshTokenHistory;
import com.erg.ergspringboot.model.auth.SecurityEvent;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.UserRoleAssignmentRepository;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionRepository sessionRepository;

    @Mock
    private AuthLoginAttemptRepository loginAttemptRepository;

    @Mock
    private SecurityEventRepository securityEventRepository;

    @Mock
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Mock
    private LoginFailureEnforcementService loginFailureEnforcementService;
    @Mock private RefreshTokenHistoryRepository refreshTokenHistoryRepository;

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
                loginAttemptRepository,
                passwordService,
                tokenService
        );
        authService.setSecurityEventRepository(securityEventRepository);
        authService.setUserRoleAssignmentRepository(userRoleAssignmentRepository);
        authService.setRefreshTokenHistoryRepository(refreshTokenHistoryRepository);
        lenient().when(userRepository.lockIdForSessionMutation(any(String.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
    }

    @Test
    void loginIssuesTokensAndStoresHashedRefreshTokenForActiveUser() {
        User user = activeUser("user-1", "active@example.com", "secret123");
        Permission lmsRead = new Permission();
        lmsRead.setName("lms.classes.read");
        Permission lcmsPublish = new Permission();
        lcmsPublish.setName("lcms.quiz.publish");
        Role role = new Role();
        role.setName("teacher");
        role.setPermissions(new java.util.LinkedHashSet<>(List.of(lmsRead, lcmsPublish)));
        user.setRoles(new java.util.LinkedHashSet<>(List.of(role)));
        when(userRepository.findForLogin("active@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(
                new LoginRequest("ACTIVE@example.com", "secret123", "web-1", "Chrome", "fingerprint", "lms", true),
                new AuthClientContext("127.0.0.1", "JUnit", "web-1", "Chrome", "fingerprint")
        );

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        verify(userRepository).save(user);
        verify(userRepository).lockIdForSessionMutation("user-1");

        AuthSession session = sessionCaptor.getValue();
        assertEquals("user-1", session.getUserId());
        assertEquals("web-1", session.getDeviceId());
        assertEquals("Chrome", session.getDeviceName());
        assertEquals("fingerprint", session.getDeviceFingerprint());
        assertEquals("127.0.0.1", session.getIpAddress());
        assertEquals(Duration.ofHours(12), Duration.between(session.getLastActiveAt(), session.getExpiresAt()));
        assertNotEquals(response.refreshToken(), session.getRefreshTokenHash());

        assertEquals("active@example.com", response.user().email());
        assertNotNull(response.user().lifecycle());
        assertEquals("FIRST_LOGIN_RESTRICTED", response.user().lifecycle().status());
        assertEquals("Bearer", response.tokenType());
        assertFalse(response.accessToken().isBlank());
        assertFalse(response.refreshToken().isBlank());
        assertEquals(900, response.expiresIn());
        assertEquals(1L, user.getLoginCount());
        assertNotNull(user.getLastLoginAt());
        assertEquals(List.of("lcms.quiz.publish", "lms.classes.read"), response.permissions());
        assertEquals(List.of("lcms", "lms"), response.portals());
    }

    @Test
    void refreshRotationRetainsOnlyPreviousTokenHashForReplayDetection() {
        String refreshToken = "refresh-token-current";
        String currentHash = tokenService.hashToken(refreshToken);
        AuthSession session = session("session-1", "user-1", "tenant-a");
        session.setRefreshTokenHash(currentHash);
        when(sessionRepository.lockByRefreshTokenHash(currentHash)).thenReturn(Optional.of(session));

        authService.refresh(new RefreshRequest(refreshToken, "device-a"),
                new AuthClientContext("203.0.113.1", "JUnit", "device-a", null, "fp-a"));

        assertEquals(currentHash, session.getPreviousRefreshTokenHash());
        assertNotNull(session.getRefreshTokenRotatedAt());
        assertNotEquals(currentHash, session.getRefreshTokenHash());
        verify(sessionRepository).save(session);
        ArgumentCaptor<RefreshTokenHistory> history = ArgumentCaptor.forClass(RefreshTokenHistory.class);
        verify(refreshTokenHistoryRepository).save(history.capture());
        assertEquals(currentHash, history.getValue().getTokenHash());
        assertEquals("session-1", history.getValue().getSessionId());
    }

    @Test
    void reusedPreviousRefreshTokenRevokesAccountSessionsAndEmitsHighSeverityEvent() {
        String stolenToken = "already-rotated-token";
        String stolenHash = tokenService.hashToken(stolenToken);
        AuthSession replayed = session("replayed", "user-1", "tenant-a");
        replayed.setPreviousRefreshTokenHash(stolenHash);
        AuthSession other = session("other", "user-1", "tenant-a");
        when(sessionRepository.lockByRefreshTokenHash(stolenHash)).thenReturn(Optional.empty());
        when(sessionRepository.findByPreviousRefreshTokenHash(stolenHash)).thenReturn(Optional.of(replayed));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "tenant-a"))
                .thenReturn(List.of(replayed, other));

        assertThrows(AuthFailure.InvalidToken.class, () -> authService.refresh(
                new RefreshRequest(stolenToken, "attacker-device"),
                new AuthClientContext("198.51.100.66", "Attacker", "attacker-device", null, "attacker-fp")));

        assertEquals("refresh_token_reuse", replayed.getRevokedReason());
        assertEquals("refresh_token_reuse", other.getRevokedReason());
        verify(sessionRepository).saveAll(List.of(replayed, other));
        ArgumentCaptor<SecurityEvent> event = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(securityEventRepository).save(event.capture());
        assertEquals("REFRESH_TOKEN_REUSE", event.getValue().getEventType());
        assertEquals("CRITICAL", event.getValue().getSeverity());
    }

    @Test
    void refreshTokenFromOlderRotationHistoryStillTriggersReplayResponse() {
        String oldToken = "token-from-several-rotations-ago";
        String oldHash = tokenService.hashToken(oldToken);
        RefreshTokenHistory history = new RefreshTokenHistory();
        history.setTokenHash(oldHash);
        history.setSessionId("session-old");
        history.setUserId("user-1");
        history.setTenantId("tenant-a");
        AuthSession session = session("session-old", "user-1", "tenant-a");
        when(sessionRepository.lockByRefreshTokenHash(oldHash)).thenReturn(Optional.empty());
        when(sessionRepository.findByPreviousRefreshTokenHash(oldHash)).thenReturn(Optional.empty());
        when(refreshTokenHistoryRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(history));
        when(sessionRepository.findBySessionIdAndTenantId("session-old", "tenant-a")).thenReturn(Optional.of(session));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "tenant-a"))
                .thenReturn(List.of(session));

        assertThrows(AuthFailure.InvalidToken.class, () -> authService.refresh(
                new RefreshRequest(oldToken, "attacker"),
                new AuthClientContext("198.51.100.9", "Attacker", "attacker", null, null)));

        assertEquals("refresh_token_reuse", session.getRevokedReason());
        verify(sessionRepository).saveAll(List.of(session));
    }

    @Test
    void superAdminRoleNameWithoutWildcardPermissionDoesNotGrantAllPortals() {
        User user = activeUser("user-role-only", "role-only@example.com", "secret123");
        Role role = new Role();
        role.setName("system.super_admin");
        role.setPermissions(new java.util.LinkedHashSet<>());
        user.setRoles(new java.util.LinkedHashSet<>(List.of(role)));
        when(userRepository.findForLogin("role-only@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(
                new LoginRequest("role-only@example.com", "secret123", "web-1", "Chrome", null, "admin", false),
                new AuthClientContext("127.0.0.1", "JUnit", "web-1", "Chrome", null)
        );

        assertEquals(List.of("admin"), response.portals());
    }

    @Test
    void loginResponseIncludesActiveScopedRolePermissionsForFrontendRouteGuards() {
        User user = activeUser("user-scoped", "scoped@example.com", "secret123");
        Permission permission = new Permission(); permission.setName("rbac.role.read");
        Role role = new Role(); role.setName("it_operator"); role.setPermissions(new java.util.LinkedHashSet<>(List.of(permission)));
        UserRoleAssignment assignment = new UserRoleAssignment(); assignment.setRole(role);
        when(userRepository.findForLogin("scoped@example.com")).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveForUser(eq("user-scoped"), eq("erg"), any(OffsetDateTime.class)))
                .thenReturn(List.of(assignment));

        AuthResponse response = authService.login(
                new LoginRequest("scoped@example.com", "secret123", "web-1", "Chrome", null, "lcms", false),
                new AuthClientContext("127.0.0.1", "JUnit", "web-1", "Chrome", null)
        );

        assertTrue(response.permissions().contains("rbac.role.read"));
    }

    @Test
    void loginAcceptsNormalizedInternalUsernameIdentifier() {
        User user = activeUser("user-username", "student@example.com", "secret123");
        user.setUsername("hs001");
        when(userRepository.findForLogin("hs001"))
                .thenReturn(Optional.of(user));

        AuthResponse response = authService.login(
                new LoginRequest(" HS001 ", "secret123", "web-1", "Chrome", "fingerprint", "elearning", false),
                new AuthClientContext("127.0.0.1", "JUnit", "web-1", "Chrome", "fingerprint")
        );

        assertEquals("student@example.com", response.user().email());
        verify(userRepository).findForLogin("hs001");
    }

    @Test
    void loginRejectsPortalOutsideDatabaseEntitlementsBeforeIssuingSession() {
        User user = activeUser("user-lms-only", "lms-only@example.com", "secret123");
        Permission permission = new Permission();
        permission.setName("lms.classes.read");
        Role role = new Role();
        role.setName("teacher");
        role.setPermissions(new java.util.LinkedHashSet<>(List.of(permission)));
        user.setRoles(new java.util.LinkedHashSet<>(List.of(role)));
        when(userRepository.findForLogin("lms-only@example.com")).thenReturn(Optional.of(user));

        assertThrows(
                AuthFailure.PortalForbidden.class,
                () -> authService.login(
                        new LoginRequest("lms-only@example.com", "secret123", "web-1", "Chrome", null, "crm", false),
                        new AuthClientContext("127.0.0.1", "JUnit", "web-1", "Chrome", null)
                )
        );

        verify(sessionRepository, never()).save(any(AuthSession.class));
    }

    @Test
    void loginRejectsPendingLocalUser() {
        User user = activeUser("user-1", "pending@example.com", "secret123");
        user.setStatus("PENDING");
        when(userRepository.findForLogin("pending@example.com")).thenReturn(Optional.of(user));

        assertThrows(
                AuthFailure.EmailNotVerified.class,
                () -> authService.login(
                        new LoginRequest("pending@example.com", "secret123", null, null, null, "lms", false),
                        new AuthClientContext("127.0.0.1", "JUnit", null, null, null)
                )
        );
    }

    @Test
    void loginAllowsInvitedUserIntoRestrictedFirstLoginSession() {
        User user = activeUser("user-invited", "invited@example.com", "secret123");
        user.setStatus("INVITED");
        user.setMustChangePassword(true);
        when(userRepository.findForLogin("invited@example.com")).thenReturn(Optional.of(user));

        var response = authService.login(
                new LoginRequest("invited@example.com", "secret123", null, null, null, "lms", false),
                new AuthClientContext("127.0.0.1", "JUnit", null, null, null)
        );

        org.junit.jupiter.api.Assertions.assertNotNull(response.accessToken());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", user.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(user.getMustChangePassword());
        verify(sessionRepository).save(any(AuthSession.class));
    }

    @Test
    void loginRejectsBannedUser() {
        User user = activeUser("user-1", "banned@example.com", "secret123");
        user.setStatus("BANNED");
        when(userRepository.findForLogin("banned@example.com")).thenReturn(Optional.of(user));

        assertThrows(
                AuthFailure.AccountLocked.class,
                () -> authService.login(
                        new LoginRequest("banned@example.com", "secret123", null, null, null, "lms", false),
                        new AuthClientContext("127.0.0.1", "JUnit", null, null, null)
                )
        );
    }

    @Test
    void loginRejectsInactiveUserBeforeIssuingSession() {
        User user = activeUser("user-inactive", "inactive@example.com", "secret123");
        user.setStatus("INACTIVE");
        when(userRepository.findForLogin("inactive@example.com")).thenReturn(Optional.of(user));

        assertThrows(
                AuthFailure.AccountDisabled.class,
                () -> authService.login(
                        new LoginRequest("inactive@example.com", "secret123", null, null, null, "lms", false),
                        new AuthClientContext("127.0.0.1", "JUnit", null, null, null)
                )
        );

        verify(sessionRepository, never()).save(any(AuthSession.class));
    }

    @Test
    void sameBrowserContextCanOpenLmsAndLcmsWithoutRevokingEitherPortalSession() {
        User user = activeUser("user-1", "active@example.com", "secret123");
        Permission lmsRead = new Permission();
        lmsRead.setName("lms.classes.read");
        Permission lcmsRead = new Permission();
        lcmsRead.setName("lcms.workspace.read");
        Role role = new Role();
        role.setName("teacher");
        role.setPermissions(new java.util.LinkedHashSet<>(List.of(lmsRead, lcmsRead)));
        user.setRoles(new java.util.LinkedHashSet<>(List.of(role)));
        AuthSession existingLms = session("session-lms", "user-1", "erg");
        existingLms.setDeviceId("browser-1");
        existingLms.setDeviceFingerprint("fp-browser-1");
        existingLms.setDeviceName("Chrome on Windows");
        existingLms.setUserAgent("Mozilla Chrome");
        existingLms.setIpAddress("10.0.0.1");
        existingLms.setPortal("lms");
        when(userRepository.findForLogin("active@example.com")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "erg"))
                .thenReturn(List.of(existingLms));

        AuthResponse response = assertDoesNotThrow(() -> authService.login(
                new LoginRequest("active@example.com", "secret123", "browser-1", "Chrome on Windows", "fp-browser-1", "lcms", false),
                new AuthClientContext("10.0.0.1", "Mozilla Chrome", "browser-1", "Chrome on Windows", "fp-browser-1")
        ));

        assertNotNull(response.accessToken());
        assertEquals(List.of("lcms", "lms"), response.portals());
        assertEquals(null, existingLms.getRevokedReason());
        verify(sessionRepository, never()).saveAll(any());
    }

    @Test
    void ipChangeAloneDoesNotRevokeTheKnownBrowserContext() {
        User user = activeUser("user-ip-change", "ip-change@example.com", "secret123");
        AuthSession existing = session("session-lms", user.getId(), "erg");
        existing.setDeviceId("browser-1");
        existing.setDeviceFingerprint("fp-browser-1");
        existing.setDeviceName("Chrome on Windows");
        existing.setUserAgent("Mozilla Chrome");
        existing.setIpAddress("10.0.0.1");
        when(userRepository.findForLogin("ip-change@example.com")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull(user.getId(), "erg"))
                .thenReturn(List.of(existing));

        AuthResponse response = assertDoesNotThrow(() -> authService.login(
                new LoginRequest("ip-change@example.com", "secret123", "browser-1", "Chrome on Windows", "fp-browser-1", "lcms", false),
                new AuthClientContext("10.0.0.2", "Mozilla Chrome", "browser-1", "Chrome on Windows", "fp-browser-1")
        ));

        assertNotNull(response.accessToken());
        assertEquals(null, existing.getRevokedReason());
        verify(sessionRepository, never()).saveAll(any());
    }

    @Test
    void newBrowserAndNewIpRevokesAllActiveAccountSessions() {
        User user = activeUser("user-new-context", "new-context@example.com", "secret123");
        AuthSession existingLms = session("session-lms", user.getId(), "erg");
        existingLms.setDeviceId("browser-1");
        existingLms.setDeviceFingerprint("fp-browser-1");
        existingLms.setIpAddress("10.0.0.1");
        AuthSession existingLcms = session("session-lcms", user.getId(), "erg");
        existingLcms.setDeviceId("browser-1");
        existingLcms.setDeviceFingerprint("fp-browser-1");
        existingLcms.setIpAddress("10.0.0.1");
        when(userRepository.findForLogin("new-context@example.com")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull(user.getId(), "erg"))
                .thenReturn(List.of(existingLms, existingLcms));

        AuthResponse response = assertDoesNotThrow(() -> authService.login(
                new LoginRequest("new-context@example.com", "secret123", "browser-2", "Firefox on Linux", "fp-browser-2", "lms", false),
                new AuthClientContext("10.0.0.2", "Mozilla Firefox", "browser-2", "Firefox on Linux", "fp-browser-2")
        ));

        assertNotNull(response.accessToken());
        assertEquals("session_replaced", existingLms.getRevokedReason());
        assertEquals("session_replaced", existingLcms.getRevokedReason());
        verify(sessionRepository).saveAll(List.of(existingLms, existingLcms));
    }

    @Test
    void fifthSessionReplacementWithinTenMinutesEmitsWarningWithoutBlockingLogin() {
        User user = activeUser("user-rapid", "rapid@example.com", "secret123");
        AuthSession existingDesktop = session("session-desktop", user.getId(), "erg");
        existingDesktop.setPlatform("WEB_DESKTOP");
        when(userRepository.findForLogin("rapid@example.com")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull(user.getId(), "erg"))
                .thenReturn(List.of(existingDesktop));
        when(securityEventRepository.countByTenantIdAndUserIdAndEventTypeAndOccurredAtAfter(
                eq("erg"),
                eq(user.getId()),
                eq("SESSION_REPLACED"),
                any(OffsetDateTime.class)
        )).thenReturn(5L);

        AuthResponse response = assertDoesNotThrow(() -> authService.login(
                new LoginRequest("rapid@example.com", "secret123", "web-new", "Chrome", null, "lms", false),
                new AuthClientContext("127.0.0.1", "JUnit", "web-new", "Chrome", null)
        ));

        assertNotNull(response.accessToken());
        assertEquals("ACTIVE", user.getStatus());
        ArgumentCaptor<SecurityEvent> eventCaptor = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(securityEventRepository, org.mockito.Mockito.times(2)).save(eventCaptor.capture());
        assertEquals(
                List.of("SESSION_REPLACED", "RAPID_SESSION_REPLACEMENT"),
                eventCaptor.getAllValues().stream().map(SecurityEvent::getEventType).toList()
        );
        assertEquals("HIGH", eventCaptor.getAllValues().get(1).getSeverity());
        assertEquals("WEB_DESKTOP", eventCaptor.getAllValues().get(1).getPlatform());
        assertEquals("WARNING", eventCaptor.getAllValues().get(1).getResult());
        assertEquals("RAPID_SESSION_REPLACEMENT", eventCaptor.getAllValues().get(1).getRiskCode());
    }

    @Test
    void repeatedFailedLoginDoesNotBlockUserOrRevokeSessions() {
        User user = activeUser("user-1", "active@example.com", "secret123");
        AuthSession existingWeb = session("session-1", "user-1", "erg");
        when(userRepository.findForLogin("active@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.countByAttemptedEmailIgnoreCaseAndResultInAndCreatedAtAfter(eq("active@example.com"), any(), any()))
                .thenReturn(5L);
        when(loginAttemptRepository.countByIpAddressAndResultInAndCreatedAtAfter(eq("127.0.0.1"), any(), any()))
                .thenReturn(1L);
        assertThrows(
                AuthFailure.InvalidCredentials.class,
                () -> authService.login(
                        new LoginRequest("active@example.com", "bad-password", "web-1", "Chrome", null, "lms", false),
                        new AuthClientContext("127.0.0.1", "JUnit", "web-1", "Chrome", null)
                )
        );

        assertEquals("ACTIVE", user.getStatus());
        assertEquals(null, existingWeb.getRevokedReason());
        verify(userRepository, never()).save(user);
        verify(sessionRepository, never()).saveAll(any());
    }

    @Test
    void invalidCredentialsDelegateToIndependentFailureTransaction() {
        User user = activeUser("user-1", "active@example.com", "secret123");
        when(userRepository.findForLogin("active@example.com")).thenReturn(Optional.of(user));
        authService.setLoginFailureEnforcementService(loginFailureEnforcementService);
        AuthClientContext context = new AuthClientContext(
                "127.0.0.1", "JUnit", "web-1", "Chrome", "fingerprint");

        assertThrows(
                AuthFailure.InvalidCredentials.class,
                () -> authService.login(
                        new LoginRequest("active@example.com", "bad-password", "web-1", "Chrome", null, "lms", false),
                        context
                )
        );

        verify(loginFailureEnforcementService)
                .recordInvalidCredentials(user, "active@example.com", context, "lms");
        verify(loginAttemptRepository, never()).save(any(AuthLoginAttempt.class));
        verify(userRepository, never()).lockIdForSessionMutation(any(String.class));
    }

    @Test
    void concurrentLoginLockTimeoutReturnsControlledLoginInProgressError() {
        User user = activeUser("user-lock", "lock@example.com", "secret123");
        when(userRepository.findForLogin("lock@example.com")).thenReturn(Optional.of(user));
        when(userRepository.lockIdForSessionMutation("user-lock"))
                .thenThrow(new QueryTimeoutException("login lock timed out"));

        assertThrows(
                AuthFailure.LoginInProgress.class,
                () -> authService.login(
                        new LoginRequest("lock@example.com", "secret123", "web-1", "Chrome", null, "lms", false),
                        new AuthClientContext("127.0.0.1", "JUnit", "web-1", "Chrome", null)
                )
        );

        verify(sessionRepository, never()).save(any(AuthSession.class));
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
    void logoutAllRevokesEveryActiveSessionForTheAccount() {
        AuthSession first = session("session-1", "user-1", "erg");
        AuthSession second = session("session-2", "user-1", "erg");
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "erg"))
                .thenReturn(List.of(first, second));

        int revoked = authService.logoutAll("user-1", "erg");

        assertEquals(2, revoked);
        assertEquals("logout_all", first.getRevokedReason());
        assertEquals("logout_all", second.getRevokedReason());
        verify(sessionRepository).saveAll(List.of(first, second));
    }

    @Test
    void listLoginAttemptsMapsUserHistoryNewestFirst() {
        AuthLoginAttempt attempt = loginAttempt("attempt-1", "user-1", "active@example.com", "FAILED");
        when(loginAttemptRepository.findByUserIdOrderByCreatedAtDesc(
                eq("user-1"), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(attempt));

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

