package com.erg.ergspringboot.repository.lms.projection;

import java.time.OffsetDateTime;

public record AttemptScoreProjection(
        String id,
        String assignmentId,
        String quizId,
        String studentId,
        String status,
        Double score,
        Double maxScore,
        Double percent,
        Boolean passed,
        OffsetDateTime startedAt,
        OffsetDateTime submittedAt,
        OffsetDateTime updatedAt
) {
}
