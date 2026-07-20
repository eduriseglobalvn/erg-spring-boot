package com.erg.ergspringboot.controller.lms.graphql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentDetailResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressSummaryResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentProgressWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.StudentGroupWorkspaceResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkSummaryResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.TeacherHomeworkWorkspaceResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.controller.lms.graphql.LmsHomeworkWorkspaceGraphQlController.AssignmentProgressWorkspaceInput;
import com.erg.ergspringboot.controller.lms.graphql.LmsHomeworkWorkspaceGraphQlController.StudentGroupsInput;
import com.erg.ergspringboot.controller.lms.graphql.LmsHomeworkWorkspaceGraphQlController.TeacherHomeworkWorkspaceInput;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.lms.LmsQueryOperations;
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
class LmsHomeworkWorkspaceGraphQlControllerTest {

    @Mock
    private LmsQueryOperations lmsReadModelService;

    private LmsHomeworkWorkspaceGraphQlController controller;

    @BeforeEach
    void setUp() {
        controller = new LmsHomeworkWorkspaceGraphQlController(
                lmsReadModelService,
                new GraphQlAccessSupport(new com.erg.ergspringboot.platform.security.ErgPolicyService()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherHomeworkWorkspaceUsesPrincipalTenantAndTeacherId() {
        authenticate(principal("teacher-1", "tenant-a", List.of("lms.assignment.read"), List.of()));
        TeacherHomeworkWorkspaceResponse expected = response("tenant-a", "teacher-1");
        when(lmsReadModelService.teacherHomeworkWorkspace("tenant-a", "teacher-1", "active", 0, 25))
                .thenReturn(expected);

        controller.teacherHomeworkWorkspace(new TeacherHomeworkWorkspaceInput(null, "active", 0, 25));

        verify(lmsReadModelService).teacherHomeworkWorkspace("tenant-a", "teacher-1", "active", 0, 25);
    }

    @Test
    void teacherHomeworkWorkspaceRejectsTenantSpoofingBeforeServiceCall() {
        authenticate(principal("teacher-1", "tenant-a", List.of("lms.assignment.read"), List.of()));

        assertThatThrownBy(() -> controller.teacherHomeworkWorkspace(
                new TeacherHomeworkWorkspaceInput("tenant-b", "active", 0, 25)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void teacherHomeworkWorkspaceRejectsMissingPermissionBeforeServiceCall() {
        authenticate(principal("teacher-1", "tenant-a", List.of("lms.class.read"), List.of()));

        assertThatThrownBy(() -> controller.teacherHomeworkWorkspace(
                new TeacherHomeworkWorkspaceInput(null, "active", 0, 25)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void teacherHomeworkWorkspaceRejectsDeniedPermissionEvenWhenGranted() {
        authenticate(principal(
                "teacher-1",
                "tenant-a",
                List.of("lms.assignment.read"),
                List.of("lms.assignment.read")
        ));

        assertThatThrownBy(() -> controller.teacherHomeworkWorkspace(
                new TeacherHomeworkWorkspaceInput(null, "active", 0, 25)
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void assignmentProgressWorkspaceUsesPrincipalTenantAndAllowsAssignmentOwner() {
        authenticate(principal("teacher-1", "tenant-a", List.of("lms.assignment.progress.read"), List.of()));
        AssignmentProgressWorkspaceResponse expected = assignmentProgressResponse("tenant-a", "class-1", "teacher-1");
        when(lmsReadModelService.assignmentProgressWorkspace("tenant-a", "assignment-1", "submitted", 0, 25))
                .thenReturn(expected);

        controller.assignmentProgressWorkspace(new AssignmentProgressWorkspaceInput(
                null,
                "assignment-1",
                "submitted",
                0,
                25
        ));

        verify(lmsReadModelService).assignmentProgressWorkspace("tenant-a", "assignment-1", "submitted", 0, 25);
    }

    @Test
    void assignmentProgressWorkspaceRejectsTenantSpoofingBeforeServiceCall() {
        authenticate(principal("teacher-1", "tenant-a", List.of("lms.assignment.progress.read"), List.of()));

        assertThatThrownBy(() -> controller.assignmentProgressWorkspace(new AssignmentProgressWorkspaceInput(
                "tenant-b",
                "assignment-1",
                null,
                0,
                25
        ))).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void assignmentProgressWorkspaceRejectsMissingPermissionBeforeServiceCall() {
        authenticate(principal("teacher-1", "tenant-a", List.of("lms.class.read"), List.of()));

        assertThatThrownBy(() -> controller.assignmentProgressWorkspace(new AssignmentProgressWorkspaceInput(
                null,
                "assignment-1",
                null,
                0,
                25
        ))).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void assignmentProgressWorkspaceRejectsDeniedPermissionEvenWithWildcardGrant() {
        authenticate(principal("teacher-1", "tenant-a", List.of("*"), List.of("lms.assignment.progress.read")));

        assertThatThrownBy(() -> controller.assignmentProgressWorkspace(new AssignmentProgressWorkspaceInput(
                null,
                "assignment-1",
                null,
                0,
                25
        ))).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    @Test
    void assignmentProgressWorkspaceRejectsResponseOutsideOwnerAndClassScope() {
        authenticate(principal("teacher-1", "tenant-a", List.of("lms.assignment.progress.read"), List.of()));
        when(lmsReadModelService.assignmentProgressWorkspace("tenant-a", "assignment-1", null, 0, 25))
                .thenReturn(assignmentProgressResponse("tenant-a", "class-denied", "teacher-2"));

        assertThatThrownBy(() -> controller.assignmentProgressWorkspace(new AssignmentProgressWorkspaceInput(
                null,
                "assignment-1",
                null,
                0,
                25
        ))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void studentGroupsUsesPrincipalTenantAndManagedClassScope() {
        authenticate(principal(
                "teacher-1",
                "tenant-a",
                List.of("lms.assignment.read"),
                List.of(),
                Map.of("managedClassIds", List.of("class-1"))
        ));
        when(lmsReadModelService.studentGroups("tenant-a", "class-1", List.of("class-1"), "teacher-1"))
                .thenReturn(new StudentGroupWorkspaceResponse("tenant-a", List.of()));

        controller.studentGroups(new StudentGroupsInput(null, "class-1"));

        verify(lmsReadModelService).studentGroups("tenant-a", "class-1", List.of("class-1"), "teacher-1");
    }

    @Test
    void studentGroupsRejectsClassOutsideManagedScopeBeforeServiceCall() {
        authenticate(principal(
                "teacher-1",
                "tenant-a",
                List.of("lms.assignment.read"),
                List.of(),
                Map.of("managedClassIds", List.of("class-1"))
        ));

        assertThatThrownBy(() -> controller.studentGroups(new StudentGroupsInput(null, "class-2")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(lmsReadModelService);
    }

    private void authenticate(ErgPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }

    private ErgPrincipal principal(
            String userId,
            String tenantId,
            List<String> permissions,
            List<String> deniedPermissions
    ) {
        return principal(userId, tenantId, permissions, deniedPermissions, Map.of());
    }

    private ErgPrincipal principal(
            String userId,
            String tenantId,
            List<String> permissions,
            List<String> deniedPermissions,
            Map<String, Object> attributes
    ) {
        return new ErgPrincipal(
                userId,
                "session-1",
                tenantId,
                List.of("teacher"),
                permissions,
                deniedPermissions,
                List.of("lms"),
                "teacher",
                "school",
                attributes
        );
    }

    private TeacherHomeworkWorkspaceResponse response(String tenantId, String teacherId) {
        return new TeacherHomeworkWorkspaceResponse(
                tenantId,
                teacherId,
                new TeacherHomeworkSummaryResponse(0, 0, 0),
                List.of(),
                new PageResponse<>(List.of(), 0, 20, 0, 0, false, false),
                new PageResponse<>(List.of(), 0, 20, 0, 0, false, false),
                new PageResponse<>(List.of(), 0, 20, 0, 0, false, false)
        );
    }

    private AssignmentProgressWorkspaceResponse assignmentProgressResponse(
            String tenantId,
            String classId,
            String assignedBy
    ) {
        return new AssignmentProgressWorkspaceResponse(
                tenantId,
                new AssignmentDetailResponse(
                        "assignment-1",
                        classId,
                        "quiz-1",
                        "math",
                        null,
                        "active",
                        assignedBy,
                        "CLASS",
                        List.of(),
                        null,
                        null
                ),
                new AssignmentProgressSummaryResponse(1, 0, 0),
                new PageResponse<>(List.of(), 0, 20, 0, 0, false, false)
        );
    }
}
