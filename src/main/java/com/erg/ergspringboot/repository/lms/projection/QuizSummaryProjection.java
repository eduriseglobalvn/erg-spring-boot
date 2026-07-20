package com.erg.ergspringboot.repository.lms.projection;

import java.time.OffsetDateTime;

public record QuizSummaryProjection(
        String id,
        String tenantId,
        String title,
        String kind,
        String subjectId,
        String levelId,
        String status,
        Integer version,
        OffsetDateTime publishedAt,
        OffsetDateTime updatedAt
) {
}
