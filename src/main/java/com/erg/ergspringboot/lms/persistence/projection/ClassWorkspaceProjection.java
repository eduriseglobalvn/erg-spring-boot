package com.erg.ergspringboot.lms.persistence.projection;

import java.time.OffsetDateTime;

public record ClassWorkspaceProjection(
        String id,
        String schoolId,
        String name,
        String grade,
        String academicYear,
        String status,
        String homeroomTeacherId,
        Long studentCount,
        Long assignmentCount,
        OffsetDateTime updatedAt
) {
}
