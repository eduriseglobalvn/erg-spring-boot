package com.erg.ergspringboot.common.request.quiz;

import com.erg.ergspringboot.model.lms.QuestionChoice;
import java.util.List;
import java.util.Map;

/** Write payloads for LCMS question-bank authoring. */
public final class QuestionBankAuthoringRequests {

    private QuestionBankAuthoringRequests() {
    }

    public record SaveQuestionRequest(
            String scopeType,
            String scopeId,
            String subjectId,
            String levelId,
            String topicId,
            String categoryId,
            String type,
            String stem,
            String objective,
            String difficulty,
            String status,
            List<String> tags,
            List<QuestionChoice> choices,
            Object answer,
            Map<String, Object> content,
            Map<String, Object> settings,
            Map<String, Object> metadata
    ) {
    }
}
