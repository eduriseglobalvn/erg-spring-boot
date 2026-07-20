package com.erg.ergspringboot.repository.lms.projection;

public record StudentDashboardScoreSummaryProjection(
        Long completedAttemptCount,
        Long attemptedAssignmentCount,
        Long passedAttemptCount,
        Double averagePercent,
        Double bestPercent
) {
}
