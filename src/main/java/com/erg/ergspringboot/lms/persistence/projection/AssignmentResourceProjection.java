package com.erg.ergspringboot.lms.persistence.projection;

public record AssignmentResourceProjection(
        String assignmentId,
        String quizId,
        String quizVersionId,
        String quizVersionLabel,
        Integer orderIndex
) {
}
