package com.erg.ergspringboot.repository.lms.projection;

public record ClassScoreSummaryProjection(
        Long completedAttemptCount,
        Long attemptedStudentCount,
        Double averagePercent,
        Double bestPercent
) {
}
