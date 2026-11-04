package com.erg.ergspringboot.lms.persistence.projection;

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
