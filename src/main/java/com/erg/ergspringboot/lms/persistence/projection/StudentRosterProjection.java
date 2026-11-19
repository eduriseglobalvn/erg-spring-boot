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
        OffsetDateTime updatedAt,
        String currentAssignmentTitle,
        String currentAttemptStatus
) {
    public StudentRosterProjection(String id, String schoolId, String academicClassId, String studentCode, String fullName,
                                   String username, String authUserId, String email, String status, OffsetDateTime updatedAt) {
        this(id, schoolId, academicClassId, studentCode, fullName, username, authUserId, email, status, updatedAt, null, null);
    }
}
