package com.erg.ergspringboot.lms.persistence.projection;

public record TeacherHomeworkSummaryProjection(
        Long activeAssignmentCount,
        Long overdueAssignmentCount,
        Long pendingReviewAssignmentCount
) {
}
