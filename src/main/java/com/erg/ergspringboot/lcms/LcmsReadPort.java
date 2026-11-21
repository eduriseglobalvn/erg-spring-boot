package com.erg.ergspringboot.lcms;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.AccessManagementWorkspaceResponse;
import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankWorkspaceResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizDraftDocumentResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizVersionHistoryResponse;
import com.erg.ergspringboot.common.response.quiz.QuizWorkspaceResponses.QuizWorkspaceResponse;

/** Public read boundary for LCMS workspaces. */
public interface LcmsReadPort {
    QuizWorkspaceResponse quizWorkspace(
            String tenantId, String search, String subjectId, String levelId, String categoryId,
            String status, String kind, Integer page, Integer size);
    QuizDraftDocumentResponse quizDraft(String tenantId, String quizId);
    QuizVersionHistoryResponse quizVersions(String tenantId, String quizId);
    /** What the caller selected: the quizzes block, the full runtime question, and how many quizzes at most. */
    record QuestionBankReadOptions(boolean quizzes, boolean runtimeQuestion, Integer quizLimit) {
        public static final QuestionBankReadOptions ALL = new QuestionBankReadOptions(true, true, null);
    }

    default QuestionBankWorkspaceResponse questionBankWorkspace(
            String tenantId, String search, String subjectId, String levelId, String topicId,
            String status, String quizKind, Boolean includeQuestions, Integer page, Integer size) {
        return questionBankWorkspace(tenantId, search, subjectId, levelId, topicId, status, quizKind, includeQuestions, page,
                size, QuestionBankReadOptions.ALL);
    }

    QuestionBankWorkspaceResponse questionBankWorkspace(
            String tenantId, String search, String subjectId, String levelId, String topicId,
            String status, String quizKind, Boolean includeQuestions, Integer page, Integer size,
            QuestionBankReadOptions options);
    AccessManagementWorkspaceResponse accessManagementWorkspace(
            String tenantId, String search, String roleId, String status, Integer page, Integer size,
            boolean includeEffectivePermissions);
}
