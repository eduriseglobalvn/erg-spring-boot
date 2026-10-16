package com.erg.ergspringboot.lcms;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.AccessManagementWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.QuestionAnalyticsResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizDraftDocumentResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizVersionHistoryResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizWorkspaceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Public LCMS read facade for workspaces and reporting projections. */
@Service
public class LcmsQueryOperations {
    private final LcmsReadPort reads;

    @Autowired
    public LcmsQueryOperations(LcmsReadPort reads) {
        this.reads = reads;
    }

    public QuizWorkspaceResponse quizWorkspace(String tenantId, String search, String subjectId, String levelId, String categoryId, String status, String kind, Integer page, Integer size) { return reads.quizWorkspace(tenantId, search, subjectId, levelId, categoryId, status, kind, page, size); }
    public QuizDraftDocumentResponse quizDraft(String tenantId, String quizId) { return reads.quizDraft(tenantId, quizId); }
    public QuizVersionHistoryResponse quizVersions(String tenantId, String quizId) { return reads.quizVersions(tenantId, quizId); }
    public QuestionBankWorkspaceResponse questionBankWorkspace(String tenantId, String search, String subjectId, String levelId, String topicId, String status, String quizKind, Boolean includeQuestions, Integer page, Integer size) { return reads.questionBankWorkspace(tenantId, search, subjectId, levelId, topicId, status, quizKind, includeQuestions, page, size); }
    public AccessManagementWorkspaceResponse accessManagementWorkspace(String tenantId, String search, String roleId, String status, Integer page, Integer size, boolean includeEffectivePermissions) { return reads.accessManagementWorkspace(tenantId, search, roleId, status, page, size, includeEffectivePermissions); }
    public QuestionAnalyticsResponse questionAnalyticsByVersion(String tenantId, String quizVersionId) { return reads.questionAnalyticsByVersion(tenantId, quizVersionId); }
    public AssignmentReportResponse assignmentReport(String tenantId, String assignmentId, Integer page, Integer size) { return reads.assignmentReport(tenantId, assignmentId, page, size); }
}
