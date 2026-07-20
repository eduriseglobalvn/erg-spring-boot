package com.erg.ergspringboot.repository.lms.projection;

public record TeacherHomeworkSummaryProjection(
        Long activeAssignmentCount,
        Long overdueAssignmentCount,
        Long pendingReviewAssignmentCount
) {
}
