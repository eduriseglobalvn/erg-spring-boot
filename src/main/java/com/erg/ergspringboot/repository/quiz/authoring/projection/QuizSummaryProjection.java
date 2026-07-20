package com.erg.ergspringboot.repository.quiz.authoring.projection;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bounded projection for the LCMS quiz workspace list (spec §4.1). Read straight from lms_quizzes
 * routing columns — no monolithic authoring blob is materialized.
 */
public record QuizSummaryProjection(
        String id,
        String title,
        String subtitle,
        String kind,
        String status,
        String subjectId,
        String levelId,
        String categoryId,
        List<String> questionIds,
        OffsetDateTime updatedAt
) {
}
