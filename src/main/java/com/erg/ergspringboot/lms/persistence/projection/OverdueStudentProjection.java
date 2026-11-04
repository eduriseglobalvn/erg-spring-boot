package com.erg.ergspringboot.lms.persistence.projection;

import java.time.OffsetDateTime;

public record OverdueStudentProjection(
        String assignmentId,
        String academicClassId,
        String quizId,
        String subjectId,
        OffsetDateTime dueAt,
        String studentId,
        String studentCode,
        String fullName,
        String status,
        OffsetDateTime updatedAt
) {
}
