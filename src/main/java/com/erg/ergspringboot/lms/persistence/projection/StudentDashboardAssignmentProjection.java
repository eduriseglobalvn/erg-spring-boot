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
        OffsetDateTime handedInAt
) {
    public StudentDashboardAssignmentProjection(String assignmentId, String academicClassId, String quizId, String subjectId,
            String subjectName, String title, OffsetDateTime dueAt, String status, OffsetDateTime assignmentUpdatedAt,
            String latestAttemptId, String latestAttemptStatus, Double latestAttemptPercent,
            OffsetDateTime latestAttemptSubmittedAt, OffsetDateTime latestAttemptUpdatedAt) {
        this(assignmentId, academicClassId, quizId, subjectId, subjectName, title, dueAt, status, assignmentUpdatedAt,
                latestAttemptId, latestAttemptStatus, latestAttemptPercent, latestAttemptSubmittedAt, latestAttemptUpdatedAt,
                null, null);
    }
}
