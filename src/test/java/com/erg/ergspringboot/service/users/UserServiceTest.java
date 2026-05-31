package com.erg.ergspringboot.service.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.users.UserRequests.OnboardingRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminCreateUserRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignPermissionsRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignRolesRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminUpdateUserStatusRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkDeleteRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkStatusRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.ChangePasswordRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.OnboardingResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.PermissionOverrideResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.RevokeSessionResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.SessionListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserCreatedResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserDetailResponse;
import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.users.SocialAccount;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.authorization.PermissionRepository;
import com.erg.ergspringboot.repository.authorization.RoleRepository;
import com.erg.ergspringboot.repository.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.repository.users.SocialAccountRepository;
import com.erg.ergspringboot.repository.users.UserActivityRepository;
import com.erg.ergspringboot.service.auth.PasswordService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionRepository sessionRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserPermissionOverrideRepository permissionOverrideRepository;

    @Mock
    private UserActivityRepository userActivityRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    private PasswordService passwordService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        userService = new UserService(
                userRepository,
                sessionRepository,
                roleRepository,
                permissionRepository,
                permissionOverrideRepository,
                userActivityRepository,
                socialAccountRepository,
                passwordService
        );
    }

    @Test
    void getCurrentUserReturnsMappedProfile() {
        User user = user("user-1");
        user.setPhone("0909000000");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        UserResponse response = userService.getCurrentUser("user-1", "erg");

        assertEquals("user-1", response.id());
        assertEquals("user-1@example.com", response.email());
        assertEquals("Active User", response.fullName());
        assertEquals("0909000000", response.phone());
        assertTrue(response.roles().contains("user"));
    }

    @Test
    void getCurrentUserThrowsWhenUserMissing() {
        when(userRepository.findByIdAndTenantId("missing", "erg")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getCurrentUser("missing", "erg"));
    }

    @Test
    void updateProfileOnlyAppliesProvidedFields() {
        User user = user("user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        UserResponse response = userService.updateProfile(
                "user-1",
                "erg",
                new UpdateProfileRequest(
                        "Updated User",
                        "0909000000",
                        null,
                        null,
                        null,
                        "1 Main St",
                        null,
                        null,
                        null,
                        null,
                        Map.of("website", "https://erg.edu.vn"),
                        "https://cdn.example/avatar.png"
                )
        );

        assertEquals("Updated User", response.fullName());
        assertEquals("0909000000", response.phone());
        assertEquals("1 Main St", response.address());
        assertEquals("https://cdn.example/avatar.png", response.avatarUrl());
        assertEquals("https://erg.edu.vn", response.socialLinks().get("website"));
        verify(userRepository).save(user);
    }

    @Test
    void onboardingMarksProfileCompleted() {
        User user = user("user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        OnboardingResponse response = userService.completeOnboarding(
                "user-1",
                "erg",
                new OnboardingRequest(
                        "Learner One",
                        "0909000000",
                        "Bio",
                        "female",
                        "2000-01-01",
                        "1 Main St",
                        "HCM",
                        "District 1",
                        "Student",
                        "VN"
                )
        );

        assertEquals("Learner One", response.fullName());
        assertTrue(response.isProfileCompleted());
        assertTrue(user.getIsProfileCompleted());
        verify(userRepository).save(user);
    }

    @Test
    void listSessionsReturnsCurrentSessionMarker() {
        AuthSession session = session("session-1", "user-1");
        when(sessionRepository.findByUserIdAndTenantIdOrderByLastActiveAtDesc("user-1", "erg"))
                .thenReturn(List.of(session));

        SessionListResponse response = userService.listSessions("user-1", "erg", "session-1");

        assertEquals(1, response.items().size());
        assertTrue(response.items().getFirst().current());
    }

    @Test
    void revokeSessionMarksSessionRevoked() {
        AuthSession session = session("session-1", "user-1");
        when(sessionRepository.findBySessionIdAndUserIdAndTenantId("session-1", "user-1", "erg"))
                .thenReturn(Optional.of(session));

        RevokeSessionResponse response = userService.revokeSession("user-1", "erg", "session-1");

        assertTrue(response.success());
        assertEquals("user_revoked", session.getRevokedReason());
        verify(sessionRepository).save(session);
    }

    @Test
    void changePasswordRequiresOldPasswordAndRevokesSessions() {
        User user = user("user-1");
        user.setPasswordHash(passwordService.hash("old-secret"));
        AuthSession session = session("session-1", "user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "erg"))
                .thenReturn(List.of(session));

        userService.changePassword("user-1", "erg", new ChangePasswordRequest("old-secret", "new-secret"));

        assertTrue(passwordService.matches("new-secret", user.getPasswordHash()));
        assertEquals("password_changed", session.getRevokedReason());
        verify(userRepository).save(user);
        verify(sessionRepository).saveAll(List.of(session));
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        User user = user("user-1");
        user.setPasswordHash(passwordService.hash("old-secret"));
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        assertThrows(
                BadRequestException.class,
                () -> userService.changePassword("user-1", "erg", new ChangePasswordRequest("wrong", "new-secret"))
        );
    }

    @Test
    void adminListUsersReturnsPaginatedResponse() {
        when(userRepository.findAll()).thenReturn(List.of(user("user-1"), user("user-2")));

        UserListResponse response = userService.listUsers(1, 1, null, null, null);

        assertEquals(1, response.users().size());
        assertEquals(2, response.meta().total());
        assertEquals(2, response.meta().totalPages());
    }

    @Test
    void adminGetUserDetailIncludesSessionsAndSocialAccounts() {
        User user = user("user-1");
        AuthSession session = session("session-1", "user-1");
        SocialAccount socialAccount = new SocialAccount();
        socialAccount.setId("social-1");
        socialAccount.setUserId("user-1");
        socialAccount.setProvider("google");
        socialAccount.setProviderId("google-123");
        socialAccount.setEmail("user-1@example.com");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdOrderByLastActiveAtDesc("user-1", "erg"))
                .thenReturn(List.of(session));
        when(socialAccountRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(socialAccount));

        UserDetailResponse response = userService.getUserDetail("user-1", "erg");

        assertEquals("user-1", response.user().id());
        assertEquals(1, response.sessions().size());
        assertEquals("google", response.socialAccounts().getFirst().provider());
    }

    @Test
    void adminListActivityReturnsPaginatedLog() {
        UserActivity activity = new UserActivity();
        activity.setId("activity-1");
        activity.setUserId("admin-1");
        activity.setTenantId("erg");
        activity.setTargetUserId("user-1");
        activity.setAction("USER_STATUS_UPDATED");
        activity.setDescription("Blocked by admin");
        activity.setCreatedAt(OffsetDateTime.parse("2026-05-20T00:00:00Z"));
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user("user-1")));
        when(userActivityRepository.findByTargetUserIdAndTenantIdOrderByCreatedAtDesc("user-1", "erg"))
                .thenReturn(List.of(activity));

        ActivityListResponse response = userService.listActivity("user-1", "erg", 1, 20);

        assertEquals(1, response.items().size());
        assertEquals("USER_STATUS_UPDATED", response.items().getFirst().action());
        assertEquals(1, response.meta().total());
    }

    @Test
    void adminCreateUserHashesPassword() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);

        UserCreatedResponse response = userService.createUser(
                new AdminCreateUserRequest("New@Example.com", "secret123", "New User", List.of("user"), "0909")
        );

        assertEquals("new@example.com", response.email());
        assertEquals("ACTIVE", response.status());
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                "new@example.com".equals(user.getEmail()) && passwordService.matches("secret123", user.getPasswordHash())
        ));
    }

    @Test
    void adminUpdateStatusSavesUser() {
        User user = user("user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        UserResponse response = userService.updateUserStatus("user-1", "erg", new AdminUpdateUserStatusRequest("BLOCKED"));

        assertEquals("BLOCKED", response.status());
        verify(userRepository).save(user);
    }

    @Test
    void adminBanRevokesActiveSessions() {
        User user = user("user-1");
        AuthSession session = session("session-1", "user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "erg"))
                .thenReturn(List.of(session));

        UserResponse response = userService.updateUserStatus("user-1", "erg", new AdminUpdateUserStatusRequest("BANNED"));

        assertEquals("BANNED", response.status());
        assertEquals("status_changed_banned", session.getRevokedReason());
        assertTrue(session.getRevokedAt() != null);
        verify(sessionRepository).saveAll(List.of(session));
    }

    @Test
    void adminUnbanDoesNotRevokeActiveSessions() {
        User user = user("user-1");
        user.setStatus("BANNED");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        UserResponse response = userService.updateUserStatus("user-1", "erg", new AdminUpdateUserStatusRequest("ACTIVE"));

        assertEquals("ACTIVE", response.status());
        verify(userRepository).save(user);
        verify(sessionRepository, org.mockito.Mockito.never())
                .findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "erg");
    }

    @Test
    void bulkUpdateStatusUpdatesAllTenantUsers() {
        User userOne = user("user-1");
        User userTwo = user("user-2");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(userOne));
        when(userRepository.findByIdAndTenantId("user-2", "erg")).thenReturn(Optional.of(userTwo));

        var response = userService.bulkUpdateStatus(
                "erg",
                new BulkStatusRequest(List.of("user-1", "user-2"), "blocked")
        );

        assertEquals(2, response.modifiedCount());
        assertEquals("BLOCKED", userOne.getStatus());
        assertEquals("BLOCKED", userTwo.getStatus());
        verify(userRepository).saveAll(List.of(userOne, userTwo));
    }

    @Test
    void bulkBanRevokesActiveSessionsForAllUsers() {
        User userOne = user("user-1");
        User userTwo = user("user-2");
        AuthSession sessionOne = session("session-1", "user-1");
        AuthSession sessionTwo = session("session-2", "user-2");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(userOne));
        when(userRepository.findByIdAndTenantId("user-2", "erg")).thenReturn(Optional.of(userTwo));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "erg"))
                .thenReturn(List.of(sessionOne));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-2", "erg"))
                .thenReturn(List.of(sessionTwo));

        var response = userService.bulkUpdateStatus(
                "erg",
                new BulkStatusRequest(List.of("user-1", "user-2"), "banned")
        );

        assertEquals(2, response.modifiedCount());
        assertEquals("status_changed_banned", sessionOne.getRevokedReason());
        assertEquals("status_changed_banned", sessionTwo.getRevokedReason());
        verify(sessionRepository).saveAll(List.of(sessionOne));
        verify(sessionRepository).saveAll(List.of(sessionTwo));
    }

    @Test
    void bulkDeleteRemovesAllTenantUsers() {
        User userOne = user("user-1");
        User userTwo = user("user-2");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(userOne));
        when(userRepository.findByIdAndTenantId("user-2", "erg")).thenReturn(Optional.of(userTwo));

        var response = userService.bulkDelete("erg", new BulkDeleteRequest(List.of("user-1", "user-2")));

        assertEquals(2, response.modifiedCount());
        verify(userRepository).deleteAll(List.of(userOne, userTwo));
    }

    @Test
    void assignRolesReplacesExistingRolesByName() {
        User user = user("user-1");
        Role admin = role("admin");
        Role teacher = role("teacher");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(roleRepository.findByNameInIgnoreCase(List.of("admin", "teacher"))).thenReturn(List.of(admin, teacher));

        UserResponse response = userService.assignRoles(
                "user-1",
                "erg",
                new AdminAssignRolesRequest(List.of("admin", "teacher"))
        );

        assertTrue(response.roles().contains("admin"));
        assertTrue(response.roles().contains("teacher"));
        verify(userRepository).save(user);
    }

    @Test
    void assignPermissionsReplacesDirectPermissionOverrides() {
        User user = user("user-1");
        Permission permission = new Permission();
        permission.setId("permission-1");
        permission.setName("users.read");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(permissionRepository.findByNameInIgnoreCase(List.of("users.read"))).thenReturn(List.of(permission));

        PermissionOverrideResponse response = userService.assignPermissions(
                "user-1",
                "erg",
                new AdminAssignPermissionsRequest(List.of("users.read"), "grant", "Manual access", null)
        );

        assertEquals("user-1", response.userId());
        assertEquals(List.of("users.read"), response.permissions());
        assertEquals("GRANT", response.grantType());
        verify(permissionOverrideRepository).deleteByUserId("user-1");
        verify(permissionOverrideRepository).saveAll(org.mockito.ArgumentMatchers.argThat(overrides -> {
            UserPermissionOverride override = overrides.iterator().next();
            return "users.read".equals(override.getPermission()) && "GRANT".equals(override.getGrantType());
        }));
    }

    private User user(String id) {
        User user = new User();
        user.setId(id);
        user.setEmail(id + "@example.com");
        user.setFullName("Active User");
        user.setStatus("ACTIVE");
        user.setProvider("local");
        user.setAccountType("erg");
        user.setTenantId("erg");
        user.setIsProfileCompleted(false);
        user.setLoginCount(0L);
        return user;
    }

    private AuthSession session(String sessionId, String userId) {
        AuthSession session = new AuthSession();
        session.setId("db-" + sessionId);
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTenantId("erg");
        session.setDeviceId("web-1");
        session.setDeviceName("Chrome");
        session.setIpAddress("127.0.0.1");
        session.setUserAgent("JUnit");
        session.setCreatedAt(OffsetDateTime.now());
        session.setLastActiveAt(OffsetDateTime.now());
        session.setExpiresAt(OffsetDateTime.now().plusDays(1));
        return session;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setId("role-" + name);
        role.setName(name);
        return role;
    }
}

