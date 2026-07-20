package com.erg.ergspringboot.repository.lms.projection;

import java.time.OffsetDateTime;

public record MissingLateStudentProjection(
        String studentId,
        String schoolId,
        String academicClassId,
        String studentCode,
        String fullName,
        String username,
        String authUserId,
        String email,
        String status,
        OffsetDateTime dueAt,
        Boolean missing,
        Boolean late,
        OffsetDateTime updatedAt
) {
}
