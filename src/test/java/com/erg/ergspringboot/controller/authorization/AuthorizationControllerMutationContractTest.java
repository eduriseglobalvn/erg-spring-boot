package com.erg.ergspringboot.controller.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.RevokeScopedRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.ScopedRoleAssignmentResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.authorization.AuthorizationService;
import com.erg.ergspringboot.service.authorization.ScopedRoleAssignmentService;
import com.erg.ergspringboot.service.users.UserService.AuditContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthorizationControllerMutationContractTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokeScopedRolePassesAuthenticatedTenantAndRequestAuditContext() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ScopedRoleAssignmentService scopedService = mock(ScopedRoleAssignmentService.class);
        AuthorizationController controller = new AuthorizationController(authorizationService);
        controller.setScopedRoleAssignmentService(scopedService);
        ErgPrincipal principal = new ErgPrincipal(
                "admin-1", "session-1", "tenant-a", List.of("admin"), List.of("rbac.role.assign"),
                List.of(), List.of("admin"), "erg", "admin", Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "DELETE", "/api/v1/users/user-1/role-assignments/assignment-1");
        request.setAttribute("requestId", "request-1");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        ScopedRoleAssignmentResponse response = new ScopedRoleAssignmentResponse(
                "assignment-1", "user-1", "role-1", "teacher", "tenant-a", "SCHOOL", "school-1",
                null, "admin-old", null, null, null, null);
        when(scopedService.revoke(
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("assignment-1"),
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                any(RevokeScopedRoleRequest.class),
                org.mockito.ArgumentMatchers.eq(principal),
                any(AuditContext.class)
        )).thenReturn(response);

        var apiResponse = controller.revokeScopedRole(
                "user-1", "assignment-1", new RevokeScopedRoleRequest("left department"), request);

        assertThat(apiResponse.data()).isEqualTo(response);
        ArgumentCaptor<AuditContext> context = ArgumentCaptor.forClass(AuditContext.class);
        verify(scopedService).revoke(
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("assignment-1"),
                org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(new RevokeScopedRoleRequest("left department")),
                org.mockito.ArgumentMatchers.eq(principal),
                context.capture()
        );
        assertThat(context.getValue()).isEqualTo(new AuditContext("admin-1", "request-1", "127.0.0.1", "JUnit"));
    }
}
