package com.erg.ergspringboot.controller.lcms.graphql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.reporting.AssignmentScopeLookup;
import com.erg.ergspringboot.reporting.AssignmentScopeLookup.AssignmentScope;
import com.erg.ergspringboot.reporting.quiz.QuizReportService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class LcmsReportGraphQlControllerScopeTest {
    private final QuizReportService reports = mock(QuizReportService.class);
    private final AssignmentScopeLookup assignments = mock(AssignmentScopeLookup.class);
    private LcmsReportGraphQlController controller;

    @BeforeEach
    void setUp() {
        controller = new LcmsReportGraphQlController(reports, new GraphQlAccessSupport(new ErgPolicyService()), assignments);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignmentReportRejectsAClassOutsideTheCallersScope() {
        authenticate(List.of("class-a"));
        when(assignments.findByTenantIdAndId("tenant-a", "as-1")).thenReturn(Optional.of(new AssignmentScope("tenant-a", "class-b")));

        assertThatThrownBy(() -> controller.assignmentReport("as-1", 0, 10)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(reports);
    }

    @Test
    void assignmentReportRejectsAnUnknownAssignment() {
        authenticate(List.of("class-a"));
        when(assignments.findByTenantIdAndId("tenant-a", "as-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.assignmentReport("as-1", 0, 10)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assignmentReportServesAClassInScope() {
        authenticate(List.of("class-a"));
        when(assignments.findByTenantIdAndId("tenant-a", "as-1")).thenReturn(Optional.of(new AssignmentScope("tenant-a", "class-a")));

        controller.assignmentReport("as-1", 0, 10);

        verify(reports).assignmentReport("tenant-a", "as-1", 0, 10);
    }

    private void authenticate(List<String> classIds) {
        ErgPrincipal principal = new ErgPrincipal("user-1", "session-1", "tenant-a", List.of("teacher"),
                List.of("lms.report.read"), List.of(), List.of("lcms"), "teacher", "tenant",
                Map.of("managedClassIds", classIds));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
