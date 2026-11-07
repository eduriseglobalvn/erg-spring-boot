package com.erg.ergspringboot.lcms.persistence.quiz.authoring.projection;

import java.time.OffsetDateTime;
import java.util.Map;

/** A quiz draft's settings blob only — enough for list columns, without loading the slide tree. */
public record QuizDraftSettingsProjection(
        String quizId,
        Map<String, Object> settings,
        OffsetDateTime updatedAt
) {
}
