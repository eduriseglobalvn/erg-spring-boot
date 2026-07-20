package com.erg.ergspringboot.repository.lms.projection;

import java.time.OffsetDateTime;

public record AssignmentDetailProjection(
        String id,
        String academicClassId,
        String quizId,
        String subjectId,
        OffsetDateTime dueAt,
        String status,
        String assignedBy,
        String recipientMode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
