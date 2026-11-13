package com.erg.ergspringboot.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

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
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserPermissionOverride;
import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserActivity;
import com.erg.ergspringboot.users.persistence.auth.AuthSessionRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.UserListItemProjection;
import com.erg.ergspringboot.users.persistence.UserRoleNameProjection;
import com.erg.ergspringboot.users.persistence.authorization.PermissionRepository;
import com.erg.ergspringboot.users.persistence.authorization.RoleRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserPermissionOverrideRepository;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignmentRepository;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
import com.erg.ergspringboot.users.persistence.organization.UserAccessScopeRepository;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.users.AccountMailSender;
import com.erg.ergspringboot.auth.internal.credential.PasswordService;
import com.erg.ergspringboot.users.RoleAssignmentAudit;
import com.erg.ergspringboot.users.RoleAssignmentAudit.UserRef;
import com.erg.ergspringboot.users.internal.create.UserProvisioningUseCase;
import com.erg.ergspringboot.users.internal.lifecycle.UserLifecycleUseCase;
import com.erg.ergspringboot.users.internal.password.UserPasswordUseCase;
import com.erg.ergspringboot.users.internal.profile.UserProfileUseCase;
import com.erg.ergspringboot.users.internal.query.UserQueryUseCase;
import com.erg.ergspringboot.users.internal.roleassignment.UserRoleAssignmentUseCase;
import com.erg.ergspringboot.users.internal.session.UserSessionUseCase;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
// Exercises UserOperations end-to-end over the real use cases (formerly via the retired UserService adapter).
class UserOperationsUseCaseTest {

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
    private UserAccessScopeRepository userAccessScopeRepository;

    @Mock
    private AccountMailSender accountMailSender;

    @Mock
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Mock
    private RoleAssignmentAudit authorizationMutationAuditService;

    private PasswordService passwordService;
    private UserOperations operations;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        operations = new UserOperations(
                new UserQueryUseCase(userRepository, sessionRepository, userActivityRepository),
                new UserProfileUseCase(userRepository, userActivityRepository),
                new UserPasswordUseCase(userRepository, sessionRepository, userActivityRepository, passwordService),
                new UserSessionUseCase(sessionRepository, userActivityRepository),
                new UserProvisioningUseCase(userRepository, sessionRepository, roleRepository,
                        userAccessScopeRepository, userActivityRepository, passwordService, accountMailSender,
                        userRoleAssignmentRepository),
                new UserLifecycleUseCase(userRepository, sessionRepository, userActivityRepository),
                new UserRoleAssignmentUseCase(userRepository, roleRepository, permissionRepository,
                        permissionOverrideRepository, userActivityRepository, authorizationMutationAuditService));
    }

    @Test
    void getCurrentUserReturnsMappedProfile() {
        User user = user("user-1");
        user.setPhone("0909000000");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        UserResponse response = operations.current("user-1", "erg");

        assertEquals("user-1", response.id());
        assertEquals("user-1@example.com", response.email());
        assertEquals("Active User", response.fullName());
        assertEquals("0909000000", response.phone());
        assertTrue(response.roles().contains("user"));
        assertEquals("FIRST_LOGIN_RESTRICTED", response.lifecycle().status());
        assertTrue(response.lifecycle().nextSteps().contains("COMPLETE_PROFILE"));
    }

    @Test
    void getCurrentUserThrowsWhenUserMissing() {
        when(userRepository.findByIdAndTenantId("missing", "erg")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> operations.current("missing", "erg"));
    }

    @Test
    void updateProfileOnlyAppliesProvidedFields() {
        User user = user("user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        UserResponse response = operations.updateProfile(
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
                ),
                null);

        assertEquals("Updated User", response.fullName());
        assertEquals("0909000000", response.phone());
        assertEquals("1 Main St", response.address());
        assertEquals("https://cdn.example/avatar.png", response.avatarUrl());
        assertEquals("https://erg.edu.vn", response.socialLinks().get("website"));
        verify(userRepository).save(user);
        verify(userActivityRepository).save(org.mockito.ArgumentMatchers.argThat(activity ->
                "ACCOUNT_PROFILE_UPDATED".equals(activity.getAction())
                        && "user-1".equals(activity.getTargetUserId())
        ));
    }

    @Test
    void onboardingMarksProfileCompleted() {
        User user = user("user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        OnboardingResponse response = operations.completeOnboarding(
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

        SessionListResponse response = operations.listSessions("user-1", "erg", "session-1");

        assertEquals(1, response.items().size());
        assertTrue(response.items().getFirst().current());
    }

    @Test
    void revokeSessionMarksSessionRevoked() {
        AuthSession session = session("session-1", "user-1");
        when(sessionRepository.findBySessionIdAndUserIdAndTenantId("session-1", "user-1", "erg"))
                .thenReturn(Optional.of(session));

        RevokeSessionResponse response = operations.revokeSession("user-1", "erg", "session-1");

        assertTrue(response.success());
        assertEquals("user_revoked", session.getRevokedReason());
        verify(sessionRepository).save(session);
    }

    @Test
    void adminSessionRevokeRecordsRequestAwareAudit() {
        AuthSession session = session("session-1", "user-1");
        when(sessionRepository.findBySessionIdAndUserIdAndTenantId("session-1", "user-1", "erg"))
                .thenReturn(Optional.of(session));

        operations.revokeSession(
                "user-1", "erg", "session-1",
                new UserAuditContext("admin-1", "request-1", "127.0.0.1", "JUnit"));

        verify(userActivityRepository).save(org.mockito.ArgumentMatchers.argThat(activity ->
                "EMPLOYEE_SESSION_REVOKED".equals(activity.getAction())
                        && "admin-1".equals(activity.getUserId())
                        && "user-1".equals(activity.getTargetUserId())
                        && "request-1".equals(activity.getRequestId())
        ));
    }

    @Test
    void changePasswordRequiresOldPasswordAndRevokesSessions() {
        User user = user("user-1");
        user.setPasswordHash(passwordService.hash("old-secret"));
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(true);
        user.setMustChangePassword(true);
        AuthSession currentSession = session("session-1", "user-1");
        AuthSession otherSession = session("session-2", "user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "erg"))
                .thenReturn(List.of(currentSession, otherSession));

        var response = operations.changePassword(
                "user-1", "erg", "session-1", new ChangePasswordRequest("old-secret", "new-secret"), null);

        assertTrue(passwordService.matches("new-secret", user.getPasswordHash()));
        assertFalse(user.getMustChangePassword());
        assertEquals("ACTIVE", user.getStatus());
        assertEquals(null, currentSession.getRevokedReason());
        assertEquals("password_changed", otherSession.getRevokedReason());
        assertEquals("ACTIVE", response.lifecycle().status());
        verify(userRepository).save(user);
        verify(sessionRepository).saveAll(List.of(otherSession));
        verify(userActivityRepository).save(org.mockito.ArgumentMatchers.argThat(activity ->
                "ACCOUNT_PASSWORD_CHANGED".equals(activity.getAction())
                        && "user-1".equals(activity.getTargetUserId())
        ));
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        User user = user("user-1");
        user.setPasswordHash(passwordService.hash("old-secret"));
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        assertThrows(
                BadRequestException.class,
                () -> operations.changePassword("user-1", "erg", null, new ChangePasswordRequest("wrong", "new-secret"), null)
        );
    }

    @Test
    void adminListUsersReturnsPaginatedResponse() {
        when(userRepository.findAdminListItems("erg", null, null, null, PageRequest.of(0, 1)))
                .thenReturn(new PageImpl<>(
                        List.of(userListItem("user-1")),
                        PageRequest.of(0, 1),
                        2
                ));
        when(userRepository.findRoleNamesByUserIds(List.of("user-1")))
                .thenReturn(List.of(new UserRoleNameProjection("user-1", "teacher")));

        UserListResponse response = operations.listUsers("erg", 1, 1, null, null, null);

        assertEquals(1, response.users().size());
        assertEquals(2, response.meta().total());
        assertEquals(2, response.meta().totalPages());
        assertEquals(List.of("teacher"), response.users().getFirst().roles());
        verify(userRepository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void adminListUsersPassesAuthenticatedTenantToRepository() {
        when(userRepository.findAdminListItems("tenant-a", null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        operations.listUsers("tenant-a", 1, 20, null, null, null);

        verify(userRepository).findAdminListItems("tenant-a", null, null, null, PageRequest.of(0, 20));
    }

    @Test
    void adminGetUserDetailIncludesSessions() {
        User user = user("user-1");
        AuthSession session = session("session-1", "user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdOrderByLastActiveAtDesc("user-1", "erg"))
                .thenReturn(List.of(session));

        UserDetailResponse response = operations.getUserDetail("user-1", "erg");

        assertEquals("user-1", response.user().id());
        assertEquals(1, response.sessions().size());
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
        when(userActivityRepository.findByTargetUserIdAndTenantIdOrderByCreatedAtDescIdDesc(
                eq("user-1"), eq("erg"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activity), PageRequest.of(0, 20), 1));

        ActivityListResponse response = operations.listActivity("user-1", "erg", 1, 20);

        assertEquals(1, response.items().size());
        assertEquals("USER_STATUS_UPDATED", response.items().getFirst().action());
        assertEquals(1, response.meta().total());
    }

    @Test
    void adminCreatesInvitedInternalUserWithOneTimeCredential() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("new@example.com")).thenReturn(false);
        when(roleRepository.findByNameInIgnoreCase(List.of("lms_teacher_standard")))
                .thenReturn(List.of(role("lms_teacher_standard")));

        UserCreatedResponse response = operations.createUser(
                new AdminCreateUserRequest(
                        "New@Example.com", "New User", "0909", "Recovery@Example.com",
                        List.of("lms_teacher_standard"),
                        List.of(new com.erg.ergspringboot.common.request.users.UserRequests.AccessPolicyRequest(
                                "school", "school-1", "lms_teacher_standard", List.of("lms")
                        )),
                        "ONE_TIME_PASSWORD"
                ),
                null);

        assertEquals("new@example.com", response.user().primaryEmail());
        assertEquals("INVITED", response.user().status());
        assertFalse(response.oneTimePassword().isBlank());
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                "new@example.com".equals(user.getUsername())
                        && "new@example.com".equals(user.getEmail())
                        && passwordService.matches(response.oneTimePassword(), user.getPasswordHash())
        ));
        ArgumentCaptor<UserActivity> activity = ArgumentCaptor.forClass(UserActivity.class);
        verify(userActivityRepository).save(activity.capture());
        assertEquals("TEACHER_ACCOUNT_PROVISIONED", activity.getValue().getAction());
        assertEquals(response.user().id(), activity.getValue().getTargetUserId());
    }

    @Test
    void adminEmailInviteAssignsRoleGroupsAndScopedPoliciesAtomically() {
        Role teacherRole = role("lms_teacher_standard");
        when(userRepository.existsByEmailIgnoreCase("teacher@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("teacher@example.com")).thenReturn(false);
        when(roleRepository.findByNameInIgnoreCase(List.of("lms_teacher_standard"))).thenReturn(List.of(teacherRole));

        UserCreatedResponse response = operations.createUser(new AdminCreateUserRequest(
                "Teacher@Example.com", "Teacher One", "0909", "Recovery@Example.com",
                List.of("lms_teacher_standard"),
                List.of(new com.erg.ergspringboot.common.request.users.UserRequests.AccessPolicyRequest(
                        "school", "school-1", "lms_teacher_standard", List.of("lms")
                )),
                "EMAIL_INVITE"
        ), null);

        assertEquals("QUEUED_FOR_DELIVERY", response.deliveryStatus());
        assertEquals(null, response.oneTimePassword());
        verify(userAccessScopeRepository).saveAll(org.mockito.ArgumentMatchers.argThat(scopes -> {
            var scope = scopes.iterator().next();
            return "school-1".equals(scope.getEducationUnitId())
                    && "lms_teacher_standard".equals(scope.getRole())
                    && "lms".equals(scope.getModules());
        }));
        verify(accountMailSender).sendTeacherInvite(
                org.mockito.ArgumentMatchers.eq("teacher@example.com"),
                org.mockito.ArgumentMatchers.eq("Teacher One"),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void createdEmployeeInheritsAuthenticatedActorTenant() {
        Role teacherRole = role("teacher");
        when(roleRepository.findByNameInIgnoreCase(List.of("teacher"))).thenReturn(List.of(teacherRole));
        ErgPrincipal actor = new ErgPrincipal(
                "admin-1", "session-1", "tenant-a", List.of("user_admin"),
                List.of("users.manage"), List.of(), List.of("admin"), "erg", "admin", Map.of()
        );

        operations.createUser(
                new AdminCreateUserRequest(
                        "tenant-user@example.com", "Tenant User", null, null,
                        List.of("teacher"),
                        List.of(new com.erg.ergspringboot.common.request.users.UserRequests.AccessPolicyRequest(
                                "school", "school-1", "teacher", List.of("lms")
                        )),
                        "ONE_TIME_PASSWORD"
                ),
                null,
                actor
        );

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                "tenant-a".equals(user.getTenantId())
        ));
    }

    @Test
    void delegatedCreateUserCannotAssignProtectedSystemRole() {
        Role superAdmin = role("system.super_admin");
        when(roleRepository.findByNameInIgnoreCase(List.of("system.super_admin")))
                .thenReturn(List.of(superAdmin));

        var request = new AdminCreateUserRequest(
                "new-admin@example.com", "New Admin", null, null,
                List.of("system.super_admin"),
                List.of(new com.erg.ergspringboot.common.request.users.UserRequests.AccessPolicyRequest(
                        "tenant", "erg", "system.super_admin", List.of("admin")
                )),
                "ONE_TIME_PASSWORD"
        );

        assertThrows(ForbiddenException.class, () -> operations.createUser(request, null));
    }

    @Test
    void adminResetCredentialRevokesSessionsAndReturnsSecretOnlyOnce() {
        User user = user("user-1");
        user.setEmail("teacher@example.com");
        AuthSession session = session("session-1", "user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "erg"))
                .thenReturn(List.of(session));

        var response = operations.resetCredential("user-1", "erg", "ONE_TIME_PASSWORD", null);

        assertEquals("READY", response.deliveryStatus());
        assertFalse(response.oneTimePassword().isBlank());
        assertTrue(passwordService.matches(response.oneTimePassword(), user.getPasswordHash()));
        assertTrue(user.getMustChangePassword());
        assertEquals("credential_reset", session.getRevokedReason());
        verify(userActivityRepository).save(org.mockito.ArgumentMatchers.argThat(activity ->
                "TEACHER_CREDENTIAL_RESET".equals(activity.getAction())
                        && "user-1".equals(activity.getTargetUserId())
        ));
    }

    @Test
    void resettingStudentCredentialDoesNotForcePasswordChange() {
        User user = user("student-1");
        user.setMustChangePassword(false);
        Role learner = new Role();
        learner.setName("lms.learner");
        user.setRoles(new java.util.LinkedHashSet<>(List.of(learner)));
        when(userRepository.findByIdAndTenantId("student-1", "erg")).thenReturn(Optional.of(user));

        var response = operations.resetCredential("student-1", "erg", "ONE_TIME_PASSWORD", null);

        assertTrue(passwordService.matches(response.oneTimePassword(), user.getPasswordHash()));
        assertFalse(user.getMustChangePassword());
    }

    @Test
    void resettingStudentAssignedThroughScopedRoleDoesNotForcePasswordChange() {
        User user = user("student-2");
        user.setMustChangePassword(false);
        Role learner = new Role();
        learner.setName("lms.learner");
        com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment assignment =
                new com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment();
        assignment.setRole(learner);
        when(userRepository.findByIdAndTenantId("student-2", "erg")).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveForUser(eq("student-2"), eq("erg"), any(OffsetDateTime.class)))
                .thenReturn(List.of(assignment));

        operations.resetCredential("student-2", "erg", "ONE_TIME_PASSWORD", null);

        assertFalse(user.getMustChangePassword());
    }

    @Test
    void adminUpdateStatusSavesUser() {
        User user = user("user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        UserResponse response = operations.updateUserStatus("user-1", "erg", new AdminUpdateUserStatusRequest("BLOCKED"));

        assertEquals("BLOCKED", response.status());
        verify(userRepository).save(user);
    }

    @Test
    void adminUpdateStatusRecordsBeforeAfterAndRequestActor() {
        User user = user("user-1");
        user.setStatus("ACTIVE");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));

        operations.updateUserStatus(
                "user-1", "erg", new AdminUpdateUserStatusRequest("INACTIVE"),
                new UserAuditContext("admin-1", "request-1", "127.0.0.1", "JUnit"));

        verify(userActivityRepository).save(org.mockito.ArgumentMatchers.argThat(activity ->
                "EMPLOYEE_STATUS_CHANGED".equals(activity.getAction())
                        && "admin-1".equals(activity.getUserId())
                        && "request-1".equals(activity.getRequestId())
                        && "ACTIVE".equals(((java.util.Map<?, ?>) activity.getMetadata().get("before")).get("status"))
                        && "INACTIVE".equals(((java.util.Map<?, ?>) activity.getMetadata().get("after")).get("status"))
        ));
    }

    @Test
    void adminBanRevokesActiveSessions() {
        User user = user("user-1");
        AuthSession session = session("session-1", "user-1");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserIdAndTenantIdAndRevokedAtIsNull("user-1", "erg"))
                .thenReturn(List.of(session));

        UserResponse response = operations.updateUserStatus("user-1", "erg", new AdminUpdateUserStatusRequest("BANNED"));

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

        UserResponse response = operations.updateUserStatus("user-1", "erg", new AdminUpdateUserStatusRequest("ACTIVE"));

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

        var response = operations.bulkUpdateStatus(
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

        var response = operations.bulkUpdateStatus(
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
    void bulkDeleteSoftDeletesAllTenantUsers() {
        User userOne = user("user-1");
        User userTwo = user("user-2");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(userOne));
        when(userRepository.findByIdAndTenantId("user-2", "erg")).thenReturn(Optional.of(userTwo));

        var response = operations.bulkDelete("erg", new BulkDeleteRequest(List.of("user-1", "user-2")));

        assertEquals(2, response.modifiedCount());
        verify(userRepository).saveAll(List.of(userOne, userTwo));
        assertEquals("DELETED", userOne.getStatus());
        assertEquals("DELETED", userTwo.getStatus());
    }

    @Test
    void assignRolesReplacesExistingRolesByName() {
        User user = user("user-1");
        Role admin = role("admin");
        Role teacher = role("teacher");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(roleRepository.findByNameInIgnoreCase(List.of("admin", "teacher"))).thenReturn(List.of(admin, teacher));

        UserResponse response = operations.assignRoles(
                "user-1",
                "erg",
                new AdminAssignRolesRequest(List.of("admin", "teacher")), null
        );

        assertTrue(response.roles().contains("admin"));
        assertTrue(response.roles().contains("teacher"));
        verify(userRepository).save(user);
        verify(userActivityRepository).save(org.mockito.ArgumentMatchers.argThat(activity ->
                "USER_ROLES_CHANGED".equals(activity.getAction())
                        && "user-1".equals(activity.getTargetUserId())
        ));
        verify(authorizationMutationAuditService).authorizationChanged(
                List.of(new UserRef("user-1", "erg")),
                "role-admin,role-teacher",
                "USER_ROLES_CHANGED",
                new UserAuditContext("user-1", null, null, null));
    }

    @Test
    void delegatedRoleAssignmentCannotAssignProtectedSystemRole() {
        User user = user("user-1");
        Role superAdmin = role("system.super_admin");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(roleRepository.findByNameInIgnoreCase(List.of("system.super_admin")))
                .thenReturn(List.of(superAdmin));

        assertThrows(
                ForbiddenException.class,
                () -> operations.assignRoles(
                        "user-1", "erg", new AdminAssignRolesRequest(List.of("system.super_admin")), null
                )
        );
    }

    @Test
    void assignPermissionsReplacesDirectPermissionOverrides() {
        User user = user("user-1");
        Permission permission = new Permission();
        permission.setId("permission-1");
        permission.setName("users.read");
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user));
        when(permissionRepository.findByNameInIgnoreCase(List.of("users.read"))).thenReturn(List.of(permission));
        authenticate(tenantAdmin());

        PermissionOverrideResponse response = operations.assignPermissions(
                "user-1",
                "erg",
                new AdminAssignPermissionsRequest(List.of("users.read"), "grant", "Manual access", null), null
        );

        assertEquals("user-1", response.userId());
        assertEquals(List.of("users.read"), response.permissions());
        assertEquals("GRANT", response.grantType());
        verify(permissionOverrideRepository).deleteByUserId("user-1");
        verify(permissionOverrideRepository).saveAll(org.mockito.ArgumentMatchers.argThat(overrides -> {
            UserPermissionOverride override = overrides.iterator().next();
            return "users.read".equals(override.getPermission()) && "GRANT".equals(override.getGrantType());
        }));
        verify(authorizationMutationAuditService).authorizationChanged(
                List.of(new UserRef("user-1", "erg")),
                "permission-1",
                "USER_PERMISSIONS_CHANGED",
                new UserAuditContext("user-1", null, null, null));
    }

    @Test
    void tenantAdminCannotGrantWildcardPermissionToSelfOrOthers() {
        Permission wildcard = new Permission();
        wildcard.setId("perm_all");
        wildcard.setName("*");
        when(userRepository.findByIdAndTenantId("admin-1", "erg")).thenReturn(Optional.of(user("admin-1")));
        when(userRepository.findByIdAndTenantId("user-1", "erg")).thenReturn(Optional.of(user("user-1")));
        when(permissionRepository.findByNameInIgnoreCase(List.of("*"))).thenReturn(List.of(wildcard));
        authenticate(tenantAdmin());

        assertThrows(ForbiddenException.class, () -> operations.assignPermissions(
                "admin-1", "erg", new AdminAssignPermissionsRequest(List.of("*"), "GRANT", null, null), null));
        assertThrows(ForbiddenException.class, () -> operations.assignPermissions(
                "user-1", "erg", new AdminAssignPermissionsRequest(List.of("*"), "GRANT", null, null), null));
        org.mockito.Mockito.verify(permissionOverrideRepository, org.mockito.Mockito.never()).deleteByUserId(any());
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private ErgPrincipal tenantAdmin() {
        return new ErgPrincipal("admin-1", "s-1", "erg", List.of("user_admin"),
                List.of("*", "users.manage", "rbac.role.assign"), List.of(), List.of(), "staff", "admin", Map.of());
    }

    private void authenticate(ErgPrincipal principal) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, List.of()));
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

    private UserListItemProjection userListItem(String id) {
        return new UserListItemProjection(
                id,
                id + "@example.com",
                "Active User",
                null,
                null,
                "ACTIVE",
                "local",
                "erg",
                null,
                false,
                null,
                OffsetDateTime.parse("2026-05-20T00:00:00Z")
        );
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

