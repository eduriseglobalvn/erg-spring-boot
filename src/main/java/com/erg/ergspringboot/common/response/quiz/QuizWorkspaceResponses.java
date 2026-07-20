package com.erg.ergspringboot.common.response.quiz;

import com.erg.ergspringboot.common.response.lcms.LcmsWorkspaceResponses.QuestionBankOptionResponse;
import java.util.List;

/**
 * GraphQL read payloads for the LCMS quiz workspace + editor hydrate (spec §4.1). Page shape mirrors the
 * existing {@code QuestionBankQuestionPage} so the FE {@code GraphQlPage<T>} maps 1:1. Draft blobs are
 * returned as stringified JSON to avoid needing a custom GraphQL scalar; the FE parses them.
 */
public final class QuizWorkspaceResponses {

    private QuizWorkspaceResponses() {
    }

    public record QuizWorkspaceResponse(
            String tenantId,
            QuizSummaryPageResponse quizzes,
            List<QuestionBankOptionResponse> subjects,
            List<QuestionBankOptionResponse> levels,
            List<QuestionBankOptionResponse> categories
    ) {
    }

    public record QuizSummaryPageResponse(
            List<QuizSummaryResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {
    }

    public record QuizSummaryResponse(
            String id,
            String title,
            String subtitle,
            String kind,
            String status,
            String subjectId,
            String levelId,
            String categoryId,
            String currentVersionLabel,
            int questionCount,
            String updatedAt
    ) {
    }

    public record QuizDraftDocumentResponse(
            String quizId,
            String draftId,
            int docVersion,
            String baseVersionLabel,
            String updatedAt,
            String settings,
            String playerTemplate,
            String tree
    ) {
    }

    public record QuizVersionHistoryResponse(
            String quizId,
            List<QuizVersionSummaryResponse> items
    ) {
    }

    public record QuizVersionSummaryResponse(
            String id,
            String versionLabel,
            int versionNumber,
            String gradingMode,
            String contentHash,
            String publishedBy,
            String publishedAt,
            boolean isActive
    ) {
    }
}
