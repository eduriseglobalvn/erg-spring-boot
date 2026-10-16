package com.erg.ergspringboot.service.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.reporting.AssignmentScopeLookup;
import com.erg.ergspringboot.reporting.AssignmentProgressFilter;
import com.erg.ergspringboot.reporting.internal.ReportingAnalyticsService;
import com.erg.ergspringboot.service.quiz.report.QuizReportService;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportingAnalyticsServiceTest {
    @Test
    void returnsExplicitKpiDefinitionsFromNormalizedSource() {
        QuizReportService reports = mock(QuizReportService.class);
        AssignmentScopeLookup scopes = mock(AssignmentScopeLookup.class);
        when(scopes.findByTenantIdAndId("tenant-a", "assignment-1"))
                .thenReturn(Optional.of(new AssignmentScopeLookup.AssignmentScope("tenant-a", "class-1")));
        when(reports.assignmentReport(eq("tenant-a"), eq("assignment-1"), any(AssignmentProgressFilter.class)))
                .thenReturn(new AssignmentReportResponse("assignment-1", 1, 1, 0, 0, 0, 100,
                        List.of(), 0, 50, 1, false));

        ReportingAnalyticsService service = new ReportingAnalyticsService(reports, scopes);
        var response = service.assignmentProgress(
                new UserAccessContext("teacher-1", "tenant-a", List.of("school-1"), List.of("class-1"), null, null),
                "assignment-1",
                new AssignmentProgressFilter("class-1", "submitted", null, null, ZoneId.of("Asia/Ho_Chi_Minh"), 0, 50));

        assertThat(response.sourceModel()).contains("student_progress");
        assertThat(response.kpis()).extracting("key")
                .containsExactly("totalRecipients", "submittedCount", "inProgressCount", "needsReviewCount",
                        "notStartedCount", "averagePercent");
        assertThat(response.filter().timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        verify(reports).assignmentReport(eq("tenant-a"), eq("assignment-1"), any(AssignmentProgressFilter.class));
    }
}
