package com.erg.ergspringboot.controller.elearning.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardProfileResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardScoreSummaryResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardSummaryResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardViewerResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.controller.elearning.graphql.StudentDashboardGraphQlController.StudentDashboardInput;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadScope;
import com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class StudentDashboardGraphQlControllerTest {

    @Mock
    private StudentDashboardReadService studentDashboardReadService;

    private StudentDashboardGraphQlController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentDashboardGraphQlController(
                studentDashboardReadService,
                new GraphQlAccessSupport(new com.erg.ergspringboot.platform.security.ErgPolicyService()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void studentDashboardBuildsReadScopeFromAuthenticatedPrincipal() {
        authenticate(principal("student-1", "tenant-a"));
        StudentDashboardResponse expected = response("tenant-a", "student-1");
        when(studentDashboardReadService.studentDashboard(any(StudentDashboardReadScope.class))).thenReturn(expected);

        StudentDashboardResponse actual = controller.studentDashboard(
                new StudentDashboardInput("tenant-a", "student-1")
        );

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<StudentDashboardReadScope> captor = ArgumentCaptor.forClass(StudentDashboardReadScope.class);
        verify(studentDashboardReadService).studentDashboard(captor.capture());
        StudentDashboardReadScope scope = captor.getValue();
        assertThat(scope.userId()).isEqualTo("student-1");
        assertThat(scope.tenantId()).isEqualTo("tenant-a");
        assertThat(scope.requestedTenantId()).isEqualTo("tenant-a");
        assertThat(scope.requestedStudentUserId()).isEqualTo("student-1");
        assertThat(scope.permissions()).contains("elearning.assignment.read_self");
    }

    @Test
    void studentDashboardPassesRequestedSelfScopeThroughForServiceEnforcement() {
        authenticate(principal("student-1", "tenant-a"));
        StudentDashboardResponse expected = response("tenant-a", "student-1");
        when(studentDashboardReadService.studentDashboard(any(StudentDashboardReadScope.class))).thenReturn(expected);

        controller.studentDashboard(new StudentDashboardInput("tenant-b", "student-2"));

        ArgumentCaptor<StudentDashboardReadScope> captor = ArgumentCaptor.forClass(StudentDashboardReadScope.class);
        verify(studentDashboardReadService).studentDashboard(captor.capture());
        StudentDashboardReadScope scope = captor.getValue();
        assertThat(scope.userId()).isEqualTo("student-1");
        assertThat(scope.tenantId()).isEqualTo("tenant-a");
        assertThat(scope.requestedTenantId()).isEqualTo("tenant-b");
        assertThat(scope.requestedStudentUserId()).isEqualTo("student-2");
        assertThat(scope.accessLevel()).isEqualTo("self");
    }

    @Test
    void studentDashboardRejectsMissingPrincipalBeforeServiceCall() {
        assertThatThrownBy(() -> controller.studentDashboard(new StudentDashboardInput(null, null)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(studentDashboardReadService);
    }

    private void authenticate(ErgPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }

    private ErgPrincipal principal(String userId, String tenantId) {
        return new ErgPrincipal(
                userId,
                "session-1",
                tenantId,
                List.of("student"),
                List.of("elearning.assignment.read_self"),
                List.of(),
                List.of("elearning"),
                "student",
                "self",
                Map.of()
        );
    }

    private StudentDashboardResponse response(String tenantId, String userId) {
        return new StudentDashboardResponse(
                tenantId,
                new StudentDashboardViewerResponse(userId, tenantId, "student", "self"),
                new StudentDashboardProfileResponse(userId, null, null, null, null, null, null, null, null),
                new StudentDashboardSummaryResponse(
                        0,
                        0,
                        0,
                        new StudentDashboardScoreSummaryResponse(0, 0, 0, 0, 0),
                        OffsetDateTime.now()
                ),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
