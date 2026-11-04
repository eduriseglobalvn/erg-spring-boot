package com.erg.ergspringboot.lms.persistence.projection;

public record ClassScoreSummaryProjection(
        Long completedAttemptCount,
        Long attemptedStudentCount,
        Double averagePercent,
        Double bestPercent
) {
}
