package com.erg.ergspringboot.controller.lcms.graphql;

import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.QuestionAnalyticsResponse;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.lcms.LcmsQueryOperations;
import com.erg.ergspringboot.lcms.authorization.LcmsReportRead;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL reads for quiz reporting (spec §4.9). Per-question analytics and assignment cohort progress,
 * both computed from the normalized runtime tables. Gated by report/grade read permissions and scoped to
 * the authenticated tenant like the sibling workspaces.
 */
@Controller
public class LcmsReportGraphQlController {

    private final LcmsQueryOperations queries;
    private final GraphQlAccessSupport accessSupport;

    public LcmsReportGraphQlController(LcmsQueryOperations queries, GraphQlAccessSupport accessSupport) {
        this.queries = queries;
        this.accessSupport = accessSupport;
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "quizQuestionAnalytics")
    @LcmsReportRead
    public QuestionAnalyticsResponse quizQuestionAnalytics(@Argument String quizVersionId) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.report.read", "lms.grade.read", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        // The schema contract is explicitly version-scoped. Calling the version method
        // also crosses the Spring cache proxy, avoiding an internal self-invocation.
        return queries.questionAnalyticsByVersion(tenantId, quizVersionId);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "assignmentReport")
    @LcmsReportRead
    public AssignmentReportResponse assignmentReport(
            @Argument String assignmentId,
            @Argument Integer page,
            @Argument Integer size
    ) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.report.read", "lms.grade.read", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        return queries.assignmentReport(tenantId, assignmentId, page, size);
    }
}
