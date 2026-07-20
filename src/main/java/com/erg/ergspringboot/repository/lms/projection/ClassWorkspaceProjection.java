package com.erg.ergspringboot.repository.lms.projection;

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
