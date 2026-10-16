package com.erg.ergspringboot.reporting.internal;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.reporting.AssignmentProgressFilter;
import com.erg.ergspringboot.reporting.AssignmentScopeLookup;
import com.erg.ergspringboot.reporting.ReportingAnalyticsPort;
import com.erg.ergspringboot.reporting.ReportingResponses;
import com.erg.ergspringboot.reporting.ReportingResponses.AssignmentProgressReportResponse;
import com.erg.ergspringboot.reporting.ReportingResponses.KpiDefinition;
import com.erg.ergspringboot.reporting.ReportingResponses.ReportFilter;
import com.erg.ergspringboot.service.quiz.report.QuizReportService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** Tenant/class-scoped reporting query over normalized LMS read models. */
@Service
public class ReportingAnalyticsService implements ReportingAnalyticsPort {
    private static final String SOURCE = "student_progress + assignment_progress_aggregates";
    private static final List<KpiDefinition> KPI_DEFINITIONS = List.of(
            new KpiDefinition("totalRecipients", "Total recipients",
                    "Count of normalized student_progress rows in the filtered assignment cohort.", SOURCE),
            new KpiDefinition("submittedCount", "Submitted",
                    "Rows whose server-authoritative status is submitted.", SOURCE),
            new KpiDefinition("inProgressCount", "In progress",
                    "Rows whose server-authoritative status is in_progress.", SOURCE),
            new KpiDefinition("needsReviewCount", "Needs review",
                    "Rows whose status is needs_review or review_required.", SOURCE),
            new KpiDefinition("notStartedCount", "Not started",
                    "Total recipients minus submitted, in-progress and needs-review rows.", SOURCE),
            new KpiDefinition("averagePercent", "Average percent",
                    "Average normalized score/percent for the filtered cohort; zero when no scored row exists.", SOURCE)
    );

    private final QuizReportService reports;
    private final AssignmentScopeLookup assignments;

    public ReportingAnalyticsService(QuizReportService reports, AssignmentScopeLookup assignments) {
        this.reports = reports;
        this.assignments = assignments;
    }

    @Override
    public AssignmentProgressReportResponse assignmentProgress(
            UserAccessContext access, String assignmentId, AssignmentProgressFilter filter) {
        AssignmentScopeLookup.AssignmentScope scope = assignments
                .findByTenantIdAndId(access.tenantId(), assignmentId)
                .filter(item -> access.tenantId().equals(item.tenantId()))
                .orElseThrow(() -> new ForbiddenException("assignment is outside the current tenant"));
        if (!access.canAccessClass(scope.academicClassId())) {
            throw new ForbiddenException("assignment is outside the current class scope");
        }
        AssignmentProgressFilter safe = filter == null
                ? new AssignmentProgressFilter(null, null, null, null, java.time.ZoneId.of("UTC"), 0, 100)
                : filter;
        if (safe.classId() != null && !safe.classId().equals(scope.academicClassId())) {
            throw new ForbiddenException("report class filter is outside the assignment scope");
        }
        if (!access.canAccessClass(safe.classId())) {
            throw new ForbiddenException("report class filter is outside the current class scope");
        }
        var report = reports.assignmentReport(access.tenantId(), assignmentId, safe);
        return new AssignmentProgressReportResponse(
                report,
                new ReportFilter(safe.classId(), safe.status(), safe.submittedFrom(), safe.submittedTo(),
                        safe.timezone().getId(), safe.page(), safe.size()),
                OffsetDateTime.now(), SOURCE, KPI_DEFINITIONS);
    }
}
