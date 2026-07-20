package com.erg.ergspringboot.common.response.quiz;

import java.util.List;

/**
 * Reporting read payloads (spec §4.9). All figures come from the normalized runtime tables
 * ({@code attempt_answers}, {@code student_progress}) so per-question analytics and cohort progress are
 * computed from graded rows, never re-derived from the monolithic attempt blob.
 */
public final class QuizReportResponses {

    private QuizReportResponses() {
    }

    /** Per-question difficulty/discrimination view for a published quiz version (spec §4.9). */
    public record QuestionAnalyticsResponse(
            String quizVersionId,
            List<QuestionAnalyticsRow> questions
    ) {
    }

    public record QuestionAnalyticsRow(
            String quizVersionQuestionId,
            String questionKind,
            long answeredCount,
            long correctCount,
            long partialCount,
            double correctRate,
            double averageAwardedPoints,
            double maxPoints
    ) {
    }

    /** Cohort progress for a single assignment (live, from student_progress) (spec §4.9). */
    public record AssignmentReportResponse(
            String assignmentId,
            int totalRecipients,
            int submittedCount,
            int inProgressCount,
            int notStartedCount,
            int needsReviewCount,
            double averagePercent,
            List<StudentProgressRow> students,
            int page,
            int size,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record StudentProgressRow(
            String studentUserId,
            String studentName,
            String status,
            int answeredCount,
            int totalQuestions,
            Double percentComplete,
            Double score,
            Boolean passed,
            String submittedAt
    ) {
    }
}
