package com.erg.ergspringboot.controller.users;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.common.request.users.UserRequests.OnboardingRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignPermissionsRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.AdminAssignRolesRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkDeleteRequest;
import com.erg.ergspringboot.common.request.users.UserRequests.BulkStatusRequest;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityListResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.ActivityResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.BulkOperationResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.Meta;
import com.erg.ergspringboot.common.response.users.UserResponses.OnboardingResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.PermissionOverrideResponse;
import com.erg.ergspringboot.common.response.users.UserResponses.UserResponse;
import com.erg.ergspringboot.exception.GlobalExceptionHandler;
import com.erg.ergspringboot.platform.web.RequestIdFilter;
import com.erg.ergspringboot.service.users.UserService;
import com.erg.ergspringboot.users.UserOperations;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerContractTest {

    @Test
    void createUserContractSupportsAtomicTeacherProvisioning() {
        List<String> requestFields = Arrays.stream(
                        com.erg.ergspringboot.common.request.users.UserRequests.AdminCreateUserRequest.class
                                .getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        List<String> responseFields = Arrays.stream(
                        com.erg.ergspringboot.common.response.users.UserResponses.UserCreatedResponse.class
                                .getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        org.junit.jupiter.api.Assertions.assertTrue(requestFields.containsAll(List.of(
                "primaryEmail", "recoveryEmail", "roleGroupIds", "policies", "credentialDelivery"
        )));
        org.junit.jupiter.api.Assertions.assertTrue(responseFields.containsAll(List.of(
                "user", "lifecycle", "deliveryStatus", "oneTimePassword"
        )));
    }

    @Test
    void userControllerExposesRecoveryEmailChallengeAndVerificationActions() {
        List<String> methods = Arrays.stream(UserController.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();
        org.junit.jupiter.api.Assertions.assertTrue(methods.contains("requestRecoveryEmailChallenge"));
        org.junit.jupiter.api.Assertions.assertTrue(methods.contains("verifyRecoveryEmailChallenge"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMeReturnsCurrentUserEnvelope() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.getCurrentUser("user-1", "erg")).thenReturn(userResponse("Active User"));
        MockMvc mockMvc = mockMvc(userService);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Request-ID", "req-users-me")
                        .header("X-User-ID", "user-1")
                        .header("X-Tenant-ID", "erg"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-users-me"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.id").value("user-1"))
                .andExpect(jsonPath("$.data.email").value("active@example.com"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
    }

    @Test
    void authProfileAliasIsNotMounted() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.getCurrentUser("user-1", "erg")).thenReturn(userResponse("Active User"));
        MockMvc mockMvc = mockMvc(userService);

        mockMvc.perform(get("/api/auth/profile")
                        .header("X-User-ID", "user-1")
                        .header("X-Tenant-ID", "erg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchMeUpdatesProfile() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.updateProfile(eq("user-1"), eq("erg"),
                org.mockito.ArgumentMatchers.any(UpdateProfileRequest.class),
                org.mockito.ArgumentMatchers.any(UserService.AuditContext.class)))
                .thenReturn(userResponse("Updated User"));
        MockMvc mockMvc = mockMvc(userService);

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType("application/json")
                        .header("X-User-ID", "user-1")
                        .header("X-Tenant-ID", "erg")
                        .content("""
                                {
                                  "full_name": "Updated User",
                                  "phone": "0909000000",
                                  "avatar_url": "https://cdn.example/avatar.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated User"));
    }

    @Test
    void onboardingReturnsCompletedProfileSignal() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.completeOnboarding(eq("user-1"), eq("erg"), org.mockito.ArgumentMatchers.any(OnboardingRequest.class)))
                .thenReturn(new OnboardingResponse(
                        "user-1",
                        "active@example.com",
                        "Learner One",
                        "https://cdn.example/avatar.png",
                        true
                ));
        MockMvc mockMvc = mockMvc(userService);

        mockMvc.perform(post("/api/v1/users/onboarding")
                        .contentType("application/json")
                        .header("X-User-ID", "user-1")
                        .header("X-Tenant-ID", "erg")
                        .content("""
                                {
                                  "fullName": "Learner One",
                                  "phone": "0909000000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isProfileCompleted").value(true));
    }

    @Test
    void oldOnboardingAliasIsNotMounted() throws Exception {
        MockMvc mockMvc = mockMvc(mock(UserService.class));

        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType("application/json")
                        .header("X-User-ID", "user-1")
                        .header("X-Tenant-ID", "erg")
                        .content("""
                                {
                                  "fullName": "Learner One"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingUserHeaderReturnsUnauthorized() throws Exception {
        MockMvc mockMvc = mockMvc(mock(UserService.class));
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors").value("UNAUTHORIZED"));
    }

    @Test
    void bulkStatusDeleteAndAssignRolesUseVersionedAdminRoutes() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.bulkUpdateStatus(eq("erg"), org.mockito.ArgumentMatchers.any(BulkStatusRequest.class)))
                .thenReturn(new BulkOperationResponse(2));
        when(userService.bulkDelete(eq("erg"), org.mockito.ArgumentMatchers.any(BulkDeleteRequest.class)))
                .thenReturn(new BulkOperationResponse(2));
        when(userService.assignRoles(eq("user-1"), eq("erg"),
                org.mockito.ArgumentMatchers.any(AdminAssignRolesRequest.class),
                org.mockito.ArgumentMatchers.any(UserService.AuditContext.class)))
                .thenReturn(userResponse("Active User"));
        MockMvc mockMvc = mockMvc(userService);

        mockMvc.perform(put("/api/v1/users/bulk/status")
                        .contentType("application/json")
                        .header("X-User-ID", "admin-1")
                        .header("X-Tenant-ID", "erg")
                        .content("""
                                {
                                  "user_ids": ["user-1", "user-2"],
                                  "status": "BLOCKED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modified_count").value(2));

        mockMvc.perform(delete("/api/v1/users/bulk")
                        .contentType("application/json")
                        .header("X-User-ID", "admin-1")
                        .header("X-Tenant-ID", "erg")
                        .content("""
                                {
                                  "user_ids": ["user-1", "user-2"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modified_count").value(2));

        mockMvc.perform(post("/api/v1/users/user-1/roles")
                        .contentType("application/json")
                        .header("X-User-ID", "admin-1")
                        .header("X-Tenant-ID", "erg")
                        .content("""
                                {
                                  "roles": ["admin", "teacher"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("user-1"));
    }

    @Test
    void adminActivityAndPermissionOverridesUseVersionedRoutes() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.listActivity(eq("user-1"), eq("erg"), eq(1), eq(20)))
                .thenReturn(new ActivityListResponse(
                        List.of(new ActivityResponse(
                                "activity-1",
                                "admin-1",
                                "USER_STATUS_UPDATED",
                                "user-1",
                                "127.0.0.1",
                                "Blocked by admin",
                                Map.of(),
                                "2026-05-20T00:00:00Z"
                        )),
                        new Meta(1, 20, 1, 1)
                ));
        when(userService.assignPermissions(eq("user-1"), eq("erg"),
                org.mockito.ArgumentMatchers.any(AdminAssignPermissionsRequest.class),
                org.mockito.ArgumentMatchers.any(UserService.AuditContext.class)))
                .thenReturn(new PermissionOverrideResponse("user-1", List.of("users.read"), "GRANT"));
        MockMvc mockMvc = mockMvc(userService);

        mockMvc.perform(get("/api/v1/users/user-1/activity")
                        .param("page", "1")
                        .param("limit", "20")
                        .header("X-User-ID", "admin-1")
                        .header("X-Tenant-ID", "erg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("USER_STATUS_UPDATED"));

        mockMvc.perform(post("/api/v1/users/user-1/permissions")
                        .contentType("application/json")
                        .header("X-User-ID", "admin-1")
                        .header("X-Tenant-ID", "erg")
                        .content("""
                                {
                                  "permissions": ["users.read"],
                                  "grant_type": "GRANT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.grantType").value("GRANT"));
    }

    private MockMvc mockMvc(UserService userService) {
        ErgPrincipal principal = new ErgPrincipal(
                "user-1", "session-1", "erg", List.of("system.super_admin"), List.of("*"),
                List.of(), List.of("*"), "erg", "super_admin", Map.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
                return MockMvcBuilders.standaloneSetup(new UserController(
                        userService,
                        new UserOperations(userService),
                        mock(com.erg.ergspringboot.service.auth.AccountVerificationService.class)
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new RequestIdFilter())
                .build();
    }

    private UserResponse userResponse(String fullName) {
        return new UserResponse(
                "user-1",
                "active@example.com",
                fullName,
                "0909000000",
                "https://cdn.example/avatar.png",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "ACTIVE",
                "local",
                "erg",
                "local",
                List.of("user"),
                Map.of(),
                false,
                null,
                0L,
                "erg",
                null,
                null,
                new com.erg.ergspringboot.common.response.users.UserResponses.AccountLifecycleResponse(
                        "FIRST_LOGIN_RESTRICTED", false, null, false, true,
                        List.of("COMPLETE_PROFILE", "VERIFY_RECOVERY_EMAIL", "CHANGE_PASSWORD")
                )
        );
    }
}
