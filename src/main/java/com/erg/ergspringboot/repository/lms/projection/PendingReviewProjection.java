package com.erg.ergspringboot.repository.lms.projection;

import java.time.OffsetDateTime;

public record PendingReviewProjection(
        String attemptId,
        String assignmentId,
        String academicClassId,
        String quizId,
        String subjectId,
        String studentId,
        String studentCode,
        String fullName,
        String status,
        Double score,
        Double maxScore,
        Double percent,
        OffsetDateTime submittedAt,
        OffsetDateTime updatedAt
) {
}
