package com.erg.ergspringboot.controller.lms.graphql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassInfoResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.ClassWorkspaceResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.controller.lms.graphql.LmsClassWorkspaceGraphQlController.ClassWorkspaceInput;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.lms.readmodel.LmsReadModelService;
import java.time.OffsetDateTime;
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
class LmsClassWorkspaceGraphQlControllerTest {

    @Mock
    private LmsReadModelService lmsReadModelService;

    private LmsClassWorkspaceGraphQlController controller;

    @BeforeEach
    void setUp() {
        controller = new LmsClassWorkspaceGraphQlController(
                lmsReadModelService,
                new GraphQlAccessSupport(new com.erg.ergspringboot.platform.security.ErgPolicyService()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void classWorkspaceUsesPrincipalTenantAndManagedClassScope() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.class.read"),
                List.of(),
                List.of("class-1"),
                List.of()
        ));
        ClassWorkspaceResponse expected = response("tenant-a", "school-1");
        when(lmsReadModelService.classWorkspace("tenant-a", "class-1", null, "active", 0, 25, "open", 1, 10, false, false))
                .thenReturn(expected);

        controller.classWorkspace(new ClassWorkspaceInput(null, "class-1", null, "active", 0, 25, "open", 1, 10));

        verify(lmsReadModelService).classWorkspace("tenant-a", "class-1", null, "active", 0, 25, "open", 1, 10, false, false);
    }

    @Test
    void classWorkspaceAllowsManagedSchoolScopeOnlyWhenRequestIsSchoolBoundBeforeRead() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.class.read"),
                List.of(),
                List.of(),
                List.of("school-1")
        ));
        ClassWorkspaceResponse expected = response("tenant-a", "school-1");
        when(lmsReadModelService.classWorkspace("tenant-a", "class-1", "school-1", null, null, null, null, null, null, false, false))
                .thenReturn(expected);

        controller.classWorkspace(new ClassWorkspaceInput(null, "class-1", "school-1", null, null, null, null, null, null));

        verify(lmsReadModelService).classWorkspace("tenant-a", "class-1", "school-1", null, null, null, null, null, null, false, false);
    }

    @Test
    void classWorkspaceRejectsSchoolScopeWithoutSchoolBoundInputBeforeRepositoryRead() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.class.read"),
                List.of(),
                List.of(),
                List.of("school-1")
        ));

        assertThatThrownBy(() -> controller.classWorkspace(
                new ClassWorkspaceInput(null, "class-1", null, null, null, null, null, null, null)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void classWorkspaceRejectsTenantSpoofing() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.class.read"),
                List.of(),
                List.of("class-1"),
                List.of("school-1")
        ));

        assertThatThrownBy(() -> controller.classWorkspace(
                new ClassWorkspaceInput("tenant-b", "class-1", "school-1", null, null, null, null, null, null)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void classWorkspaceRejectsUnmanagedSchoolScope() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.class.read"),
                List.of(),
                List.of(),
                List.of("school-1")
        ));

        assertThatThrownBy(() -> controller.classWorkspace(
                new ClassWorkspaceInput(null, "class-1", "school-2", null, null, null, null, null, null)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void classWorkspaceRejectsReturnedClassOutsideManagedSchoolScope() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.class.read"),
                List.of(),
                List.of(),
                List.of("school-1")
        ));
        when(lmsReadModelService.classWorkspace("tenant-a", "class-1", "school-1", null, null, null, null, null, null, false, false))
                .thenReturn(response("tenant-a", "school-2"));

        assertThatThrownBy(() -> controller.classWorkspace(
                new ClassWorkspaceInput(null, "class-1", "school-1", null, null, null, null, null, null)
        )).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void classWorkspaceRejectsDeniedPermissionEvenWhenGranted() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.class.read"),
                List.of("lms.class.read"),
                List.of("class-1"),
                List.of("school-1")
        ));

        assertThatThrownBy(() -> controller.classWorkspace(
                new ClassWorkspaceInput(null, "class-1", "school-1", null, null, null, null, null, null)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void classWorkspaceRejectsMissingPermission() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.resource.read"),
                List.of(),
                List.of("class-1"),
                List.of("school-1")
        ));

        assertThatThrownBy(() -> controller.classWorkspace(
                new ClassWorkspaceInput(null, "class-1", "school-1", null, null, null, null, null, null)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void classWorkspaceIncludesScoreSummaryOnlyWithGradePermission() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.class.read", "lms.grade.read"),
                List.of(),
                List.of("class-1"),
                List.of()
        ));
        ClassWorkspaceResponse expected = response("tenant-a", "school-1");
        when(lmsReadModelService.classWorkspace("tenant-a", "class-1", null, null, null, null, null, null, null, true, true))
                .thenReturn(expected);

        controller.classWorkspace(new ClassWorkspaceInput(null, "class-1", null, null, null, null, null, null, null));

        verify(lmsReadModelService).classWorkspace("tenant-a", "class-1", null, null, null, null, null, null, null, true, true);
    }

    @Test
    void classWorkspaceIncludesRiskSummaryWithProgressPermissionWithoutScoreSummary() {
        authenticate(principal(
                "tenant-a",
                List.of("lms.class.read", "lms.progress.read"),
                List.of(),
                List.of("class-1"),
                List.of()
        ));
        ClassWorkspaceResponse expected = response("tenant-a", "school-1");
        when(lmsReadModelService.classWorkspace("tenant-a", "class-1", null, null, null, null, null, null, null, false, true))
                .thenReturn(expected);

        controller.classWorkspace(new ClassWorkspaceInput(null, "class-1", null, null, null, null, null, null, null));

        verify(lmsReadModelService).classWorkspace("tenant-a", "class-1", null, null, null, null, null, null, null, false, true);
    }

    private void authenticate(ErgPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }

    private ErgPrincipal principal(
            String tenantId,
            List<String> permissions,
            List<String> deniedPermissions,
            List<String> managedClassIds,
            List<String> managedEducationUnitIds
    ) {
        return new ErgPrincipal(
                "teacher-1",
                "session-1",
                tenantId,
                List.of("teacher"),
                permissions,
                deniedPermissions,
                List.of("lms"),
                "teacher",
                "school",
                Map.of(
                        "managedClassIds", managedClassIds,
                        "managedEducationUnitIds", managedEducationUnitIds
                )
        );
    }

    private ClassWorkspaceResponse response(String tenantId, String schoolId) {
        return new ClassWorkspaceResponse(
                tenantId,
                new ClassInfoResponse(
                        "class-1",
                        schoolId,
                        "10A1",
                        "10",
                        "2026",
                        "active",
                        "teacher-1",
                        30,
                        4,
                        OffsetDateTime.now()
                ),
                null,
                null,
                new PageResponse<>(List.of(), 0, 20, 0, 0, false, false),
                new PageResponse<>(List.of(), 0, 20, 0, 0, false, false)
        );
    }
}
