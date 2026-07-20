package com.erg.ergspringboot.repository.lms.projection;

import com.erg.ergspringboot.model.lms.QuestionChoice;
import com.erg.ergspringboot.model.lms.ContentScope;
import java.time.OffsetDateTime;
import java.util.List;

public record QuestionBankQuestionProjection(
        String id,
        ContentScope scope,
        String subjectId,
        String subjectLabel,
        String levelId,
        String levelLabel,
        String topicId,
        String topicLabel,
        String title,
        String objective,
        String type,
        String difficulty,
        String status,
        List<String> tags,
        List<QuestionChoice> choices,
        Object answer,
        Double masteryRate,
        Integer usageCount,
        Integer schoolsUsing,
        OffsetDateTime updatedAt
) {
}
