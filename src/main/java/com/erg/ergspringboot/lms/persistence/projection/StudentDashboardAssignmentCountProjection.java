package com.erg.ergspringboot.lms.persistence.projection;

/**
 * Bounded aggregate counts for the student dashboard summary.
 *
 * <p>Keeping both counters in one projection prevents the dashboard from
 * issuing one full eligibility query per counter.</p>
 */
public record StudentDashboardAssignmentCountProjection(
        Long activeAssignmentCount,
        Long pendingActionCount
) {
}
