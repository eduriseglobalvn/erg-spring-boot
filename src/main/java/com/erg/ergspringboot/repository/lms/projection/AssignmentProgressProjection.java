package com.erg.ergspringboot.repository.lms.projection;

public record AssignmentProgressProjection(
        Long submitted,
        Long inProgress,
        Long needsReview
) {
    public int submittedCount() {
        return count(submitted);
    }

    public int inProgressCount() {
        return count(inProgress);
    }

    public int needsReviewCount() {
        return count(needsReview);
    }

    private int count(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }
}
