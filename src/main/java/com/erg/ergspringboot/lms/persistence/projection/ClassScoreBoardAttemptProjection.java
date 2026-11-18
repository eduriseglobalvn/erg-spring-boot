package com.erg.ergspringboot.lms.persistence.projection;

import java.time.OffsetDateTime;

/** One handed-in attempt of a class, with the quiz and (when the quiz belongs to one) its topic. */
public record ClassScoreBoardAttemptProjection(
        String id,
        String studentId,
        String assignmentId,
        String assignmentTitle,
        String quizId,
        String quizTitle,
        String topicId,
        String topicName,
        Double score,
        Double maxScore,
        Double percent,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime submittedAt
) {
}
