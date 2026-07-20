package com.erg.ergspringboot.controller.lcms.graphql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.AccessManagementWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.controller.lcms.graphql.LcmsWorkspaceGraphQlController.AccessManagementWorkspaceInput;
import com.erg.ergspringboot.controller.lcms.graphql.LcmsWorkspaceGraphQlController.QuestionBankWorkspaceInput;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.lcms.readmodel.LcmsWorkspaceReadService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LcmsWorkspaceGraphQlControllerTest {

    @Mock
    private LcmsWorkspaceReadService readService;

    private LcmsWorkspaceGraphQlController controller;

    @BeforeEach
    void setUp() {
        controller = new LcmsWorkspaceGraphQlController(
                new GraphQlAccessSupport(new com.erg.ergspringboot.platform.security.ErgPolicyService()),
                readService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void questionBankWorkspaceRequiresQuizReadAndUsesPrincipalTenant() {
        authenticate(principal("tenant-a", List.of("lms.quiz.read"), List.of()));
        when(readService.questionBankWorkspace("tenant-a", "algebra", "math", null, null, "active", "test", false, 0, 25))
                .thenReturn(questionBank("tenant-a"));

        controller.questionBankWorkspace(new QuestionBankWorkspaceInput(
                null, "algebra", "math", null, null, "active", "test", false, 0, 25
        ));

        verify(readService).questionBankWorkspace("tenant-a", "algebra", "math", null, null, "active", "test", false, 0, 25);
    }

    @Test
    void questionBankWorkspaceRejectsTenantSpoofingBeforeServiceCall() {
        authenticate(principal("tenant-a", List.of("lms.quiz.read"), List.of()));

        assertThatThrownBy(() -> controller.questionBankWorkspace(
                new QuestionBankWorkspaceInput("tenant-b", null, null, null, null, null, null, null, null, null)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(readService);
    }

    @Test
    void accessManagementWorkspaceRequiresScopeReadAndHidesEffectivePermissionsWithoutRbacPermission() {
        authenticate(principal("tenant-a", List.of("lms.scope.read"), List.of()));
        when(readService.accessManagementWorkspace("tenant-a", "vuong", "teacher", "active", 0, 25, false))
                .thenReturn(access("tenant-a", false));

        controller.accessManagementWorkspace(new AccessManagementWorkspaceInput(
                null, "vuong", "teacher", "active", 0, 25
        ));

        verify(readService).accessManagementWorkspace("tenant-a", "vuong", "teacher", "active", 0, 25, false);
    }

    @Test
    void accessManagementWorkspaceRejectsMissingScopeReadBeforeServiceCall() {
        authenticate(principal("tenant-a", List.of("rbac.permission.read"), List.of()));

        assertThatThrownBy(() -> controller.accessManagementWorkspace(
                new AccessManagementWorkspaceInput(null, null, null, null, null, null)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(readService);
    }

    @Test
    void accessManagementWorkspaceRejectsTenantSpoofingBeforeServiceCall() {
        authenticate(principal("tenant-a", List.of("lms.scope.read", "rbac.permission.read"), List.of()));

        assertThatThrownBy(() -> controller.accessManagementWorkspace(
                new AccessManagementWorkspaceInput("tenant-b", null, null, null, null, null)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(readService);
    }

    @Test
    void accessManagementWorkspaceIncludesEffectivePermissionsWhenAllowed() {
        authenticate(principal("tenant-a", List.of("lms.scope.read", "rbac.permission.read"), List.of()));
        when(readService.accessManagementWorkspace("tenant-a", null, null, null, null, null, true))
                .thenReturn(access("tenant-a", true));

        controller.accessManagementWorkspace(new AccessManagementWorkspaceInput(null, null, null, null, null, null));

        verify(readService).accessManagementWorkspace("tenant-a", null, null, null, null, null, true);
    }

    @Test
    void accessManagementWorkspaceIncludesEffectivePermissionsForUsersManage() {
        authenticate(principal("tenant-a", List.of("lms.scope.read", "users.manage"), List.of()));
        when(readService.accessManagementWorkspace("tenant-a", null, null, null, null, null, true))
                .thenReturn(access("tenant-a", true));

        controller.accessManagementWorkspace(new AccessManagementWorkspaceInput(null, null, null, null, null, null));

        verify(readService).accessManagementWorkspace("tenant-a", null, null, null, null, null, true);
    }

    @Test
    void accessManagementWorkspaceDeniedRbacPermissionWinsEffectivePermissionGate() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.scope.read", "rbac.permission.read"),
                List.of("rbac.permission.read")
        ));
        when(readService.accessManagementWorkspace("tenant-a", null, null, null, null, null, false))
                .thenReturn(access("tenant-a", false));

        controller.accessManagementWorkspace(new AccessManagementWorkspaceInput(null, null, null, null, null, null));

        verify(readService).accessManagementWorkspace("tenant-a", null, null, null, null, null, false);
    }

    @Test
    void accessManagementWorkspaceDeniedWildcardWinsOverWildcardGrantBeforeServiceCall() {
        authenticate(principal(
                "tenant-a",
                List.of("*"),
                List.of("*")
        ));

        assertThatThrownBy(() -> controller.accessManagementWorkspace(
                new AccessManagementWorkspaceInput(null, null, null, null, null, null)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(readService);
    }

    private void authenticate(ErgPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }

    private ErgPrincipal principal(String tenantId, List<String> permissions, List<String> deniedPermissions) {
        return new ErgPrincipal(
                "user-1",
                "session-1",
                tenantId,
                List.of("admin"),
                permissions,
                deniedPermissions,
                List.of("lcms"),
                "admin",
                "tenant",
                Map.of()
        );
    }

    private QuestionBankWorkspaceResponse questionBank(String tenantId) {
        return new QuestionBankWorkspaceResponse(
                tenantId,
                new PageResponse<>(List.of(), 0, 20, 0, 0, false, false),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private AccessManagementWorkspaceResponse access(String tenantId, boolean effectivePermissionsIncluded) {
        return new AccessManagementWorkspaceResponse(
                tenantId,
                new PageResponse<>(List.of(), 0, 20, 0, 0, false, false),
                List.of(),
                List.of(),
                effectivePermissionsIncluded
        );
    }
}
