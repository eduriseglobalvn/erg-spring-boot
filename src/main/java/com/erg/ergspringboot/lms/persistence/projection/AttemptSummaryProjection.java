package com.erg.ergspringboot.lms.persistence.projection;

import java.time.OffsetDateTime;

public record AttemptSummaryProjection(
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
