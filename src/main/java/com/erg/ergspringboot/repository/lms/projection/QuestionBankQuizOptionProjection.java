package com.erg.ergspringboot.repository.lms.projection;

import java.util.List;
import java.time.OffsetDateTime;

public record QuestionBankQuizOptionProjection(
        String id,
        String title,
        String status,
        String kind,
        String subjectId,
        String levelId,
        String categoryId,
        List<String> questionIds,
        String ownerUserId,
        OffsetDateTime updatedAt
) {
}
