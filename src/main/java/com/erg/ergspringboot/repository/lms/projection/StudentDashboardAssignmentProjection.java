package com.erg.ergspringboot.repository.lms.projection;

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
        OffsetDateTime latestAttemptUpdatedAt
) {
}
