package com.erg.ergspringboot.lms.persistence.projection;

import java.time.OffsetDateTime;

public record StudentRosterProjection(
        String id,
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
