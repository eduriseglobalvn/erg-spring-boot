package com.erg.ergspringboot.repository.lms.projection;

public record AssignmentResourceProjection(
        String assignmentId,
        String quizId,
        String quizVersionId,
        String quizVersionLabel,
        Integer orderIndex
) {
}
