package com.erg.ergspringboot.lcms;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.AccessManagementWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.QuestionAnalyticsResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizDraftDocumentResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizVersionHistoryResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizWorkspaceResponse;

/** Public read boundary for LCMS workspaces and reporting projections. */
public interface LcmsReadPort {
    QuizWorkspaceResponse quizWorkspace(
            String tenantId, String search, String subjectId, String levelId, String categoryId,
            String status, String kind, Integer page, Integer size);
    QuizDraftDocumentResponse quizDraft(String tenantId, String quizId);
    QuizVersionHistoryResponse quizVersions(String tenantId, String quizId);
    QuestionBankWorkspaceResponse questionBankWorkspace(
            String tenantId, String search, String subjectId, String levelId, String topicId,
            String status, String quizKind, Boolean includeQuestions, Integer page, Integer size);
    AccessManagementWorkspaceResponse accessManagementWorkspace(
            String tenantId, String search, String roleId, String status, Integer page, Integer size,
            boolean includeEffectivePermissions);
    QuestionAnalyticsResponse questionAnalyticsByVersion(String tenantId, String quizVersionId);
    AssignmentReportResponse assignmentReport(String tenantId, String assignmentId, Integer page, Integer size);
}
