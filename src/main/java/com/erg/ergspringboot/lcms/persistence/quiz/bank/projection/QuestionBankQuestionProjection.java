package com.erg.ergspringboot.lcms.persistence.quiz.bank.projection;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionChoice;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.ContentScope;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

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
        OffsetDateTime updatedAt,
        Map<String, Object> content
) {
    /** The row without its content jsonb: for callers that do not select runtimeQuestion. */
    public QuestionBankQuestionProjection(String id, ContentScope scope, String subjectId, String subjectLabel, String levelId,
            String levelLabel, String topicId, String topicLabel, String title, String objective, String type, String difficulty,
            String status, List<String> tags, List<QuestionChoice> choices, Object answer, Double masteryRate, Integer usageCount,
            Integer schoolsUsing, OffsetDateTime updatedAt) {
        this(id, scope, subjectId, subjectLabel, levelId, levelLabel, topicId, topicLabel, title, objective, type, difficulty,
                status, tags, choices, answer, masteryRate, usageCount, schoolsUsing, updatedAt, null);
    }
}
