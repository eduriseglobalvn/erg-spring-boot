package com.erg.ergspringboot.lms.persistence.projection;

public record StudentDashboardScoreSummaryProjection(
        Long completedAttemptCount,
        Long attemptedAssignmentCount,
        Long passedAttemptCount,
        Double averagePercent,
        Double bestPercent
) {
}
