package com.erg.ergspringboot.lms;

import java.time.OffsetDateTime;

/**
 * Published inside the submit transaction; listeners run after commit. Delivery is at-least-once.
 * {@code status} is the post-submit attempt state, so {@code needs_review} marks a score that is not final yet.
 */
public record LmsAttemptSubmitted(
        String eventId,
        String tenantId,
        String attemptId,
        String assignmentId,
        String studentId,
        String status,
        double score,
        double maxScore,
        boolean passed,
        OffsetDateTime occurredAt
) {
}
