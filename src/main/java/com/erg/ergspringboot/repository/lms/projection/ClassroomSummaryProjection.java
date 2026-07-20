package com.erg.ergspringboot.repository.lms.projection;

import java.time.OffsetDateTime;

public record ClassroomSummaryProjection(
        String id,
        String tenantId,
        String schoolId,
        String name,
        String grade,
        String academicYear,
        String status,
        String homeroomTeacherId,
        OffsetDateTime updatedAt
) {
}
