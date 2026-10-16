package com.erg.ergspringboot.reporting;

import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import java.time.OffsetDateTime;
import java.util.List;

/** Stable reporting metadata around the normalized LMS read models. */
public final class ReportingResponses {
    private ReportingResponses() { }

    public record KpiDefinition(
            String key,
            String label,
            String definition,
            String sourceModel
    ) { }

    public record ReportFilter(
            String classId,
            String status,
            OffsetDateTime submittedFrom,
            OffsetDateTime submittedTo,
            String timezone,
            int page,
            int size
    ) { }

    public record AssignmentProgressReportResponse(
            AssignmentReportResponse report,
            ReportFilter filter,
            OffsetDateTime generatedAt,
            String sourceModel,
            List<KpiDefinition> kpis
    ) { }
}
