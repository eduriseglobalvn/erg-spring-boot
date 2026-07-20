package com.erg.ergspringboot.lcms;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.AccessManagementWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.QuestionAnalyticsResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizDraftDocumentResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizVersionHistoryResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizWorkspaceResponse;
import com.erg.ergspringboot.service.lcms.readmodel.LcmsQuizWorkspaceReadService;
import com.erg.ergspringboot.service.lcms.readmodel.LcmsWorkspaceReadService;
import com.erg.ergspringboot.service.quiz.report.QuizReportService;
import org.springframework.stereotype.Service;

/** Public read boundary for LCMS workspaces and reporting projections. */
@Service
public class LcmsQueryOperations {
    private final LcmsQuizWorkspaceReadService quizWorkspaces;
    private final LcmsWorkspaceReadService workspaces;
    private final QuizReportService reports;

    public LcmsQueryOperations(
            LcmsQuizWorkspaceReadService quizWorkspaces,
            LcmsWorkspaceReadService workspaces,
            QuizReportService reports
    ) {
        this.quizWorkspaces = quizWorkspaces;
        this.workspaces = workspaces;
        this.reports = reports;
    }

    public QuizWorkspaceResponse quizWorkspace(
            String tenantId, String search, String subjectId, String levelId, String categoryId,
            String status, String kind, Integer page, Integer size
    ) {
        return quizWorkspaces.quizWorkspace(tenantId, search, subjectId, levelId, categoryId, status, kind, page, size);
    }

    public QuizDraftDocumentResponse quizDraft(String tenantId, String quizId) {
        return quizWorkspaces.quizDraft(tenantId, quizId);
    }

    public QuizVersionHistoryResponse quizVersions(String tenantId, String quizId) {
        return quizWorkspaces.quizVersions(tenantId, quizId);
    }

    public QuestionBankWorkspaceResponse questionBankWorkspace(
            String tenantId, String search, String subjectId, String levelId, String topicId, String status,
            String quizKind, Boolean includeQuestions, Integer page, Integer size
    ) {
        return workspaces.questionBankWorkspace(tenantId, search, subjectId, levelId, topicId, status,
                quizKind, includeQuestions, page, size);
    }

    public AccessManagementWorkspaceResponse accessManagementWorkspace(
            String tenantId, String search, String roleId, String status, Integer page, Integer size,
            boolean includeEffectivePermissions
    ) {
        return workspaces.accessManagementWorkspace(tenantId, search, roleId, status, page, size,
                includeEffectivePermissions);
    }

    public QuestionAnalyticsResponse questionAnalyticsByVersion(String tenantId, String quizVersionId) {
        return reports.questionAnalyticsByVersion(tenantId, quizVersionId);
    }

    public AssignmentReportResponse assignmentReport(String tenantId, String assignmentId, Integer page, Integer size) {
        return reports.assignmentReport(tenantId, assignmentId, page, size);
    }
}
