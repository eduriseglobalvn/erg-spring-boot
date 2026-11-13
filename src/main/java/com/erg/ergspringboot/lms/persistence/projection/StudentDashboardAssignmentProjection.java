package com.erg.ergspringboot.lms.persistence.projection;

import java.time.OffsetDateTime;

public record StudentDashboardAssignmentProjection(
        String assignmentId,
        String academicClassId,
        String quizId,
        String subjectId,
        String subjectName,
        String title,
        OffsetDateTime dueAt,
        String status,
        OffsetDateTime assignmentUpdatedAt,
        String latestAttemptId,
        String latestAttemptStatus,
        Double latestAttemptPercent,
        OffsetDateTime latestAttemptSubmittedAt,
        OffsetDateTime latestAttemptUpdatedAt,
        /** Latest handed-in attempt (a retake in progress keeps the last score); the dashboard average uses the same one. */
        Double handedInPercent,
        OffsetDateTime handedInAt,
        Integer maxDurationMinutes,
        String teacherName,
        /** Quiz kind: {@code train} or {@code test}. */
        String quizKind,
        /** Questions listed on the quiz; null when it lists none. */
        Integer questionCount,
        /** Handed-in attempts of this student for the assignment. */
        Long attemptCount,
        Double handedInScore,
        Double handedInMaxScore,
        Integer handedInTotalQuestions,
        Boolean handedInLate,
        /** Answers saved so far on the latest attempt ("Đang làm 12/20"). */
        Integer latestAnsweredCount,
        Integer latestTotalQuestions
) {
    public StudentDashboardAssignmentProjection(String assignmentId, String academicClassId, String quizId, String subjectId,
            String subjectName, String title, OffsetDateTime dueAt, String status, OffsetDateTime assignmentUpdatedAt,
            String latestAttemptId, String latestAttemptStatus, Double latestAttemptPercent,
            OffsetDateTime latestAttemptSubmittedAt, OffsetDateTime latestAttemptUpdatedAt,
            Double handedInPercent, OffsetDateTime handedInAt) {
        this(assignmentId, academicClassId, quizId, subjectId, subjectName, title, dueAt, status, assignmentUpdatedAt,
                latestAttemptId, latestAttemptStatus, latestAttemptPercent, latestAttemptSubmittedAt, latestAttemptUpdatedAt,
                handedInPercent, handedInAt, null, null, null, null, null, null, null, null, null, null, null);
    }

    public StudentDashboardAssignmentProjection(String assignmentId, String academicClassId, String quizId, String subjectId,
            String subjectName, String title, OffsetDateTime dueAt, String status, OffsetDateTime assignmentUpdatedAt,
            String latestAttemptId, String latestAttemptStatus, Double latestAttemptPercent,
            OffsetDateTime latestAttemptSubmittedAt, OffsetDateTime latestAttemptUpdatedAt) {
        this(assignmentId, academicClassId, quizId, subjectId, subjectName, title, dueAt, status, assignmentUpdatedAt,
                latestAttemptId, latestAttemptStatus, latestAttemptPercent, latestAttemptSubmittedAt, latestAttemptUpdatedAt,
                null, null);
    }
}
