package com.erg.ergspringboot.lms.persistence.projection;

import java.time.OffsetDateTime;

public record RecipientProgressProjection(
        String studentId,
        String schoolId,
        String academicClassId,
        String studentCode,
        String fullName,
        String username,
        String authUserId,
        String email,
        String status,
        OffsetDateTime updatedAt
) {
}
