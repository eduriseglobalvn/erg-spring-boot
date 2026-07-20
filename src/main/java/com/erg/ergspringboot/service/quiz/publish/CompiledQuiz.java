package com.erg.ergspringboot.service.quiz.publish;

import java.util.List;
import java.util.Map;

/**
 * Result of compiling an authoring draft into a runtime quiz (spec §3). Splits correctness out of the
 * player-facing body: {@code runtimeQuiz} + per-question {@code body} carry NO answer key, while
 * {@code answerKey} (keyed by runtime question id) holds the secret correctness data kept server-side.
 */
public record CompiledQuiz(
        List<CompiledSection> sections,
        List<CompiledQuestion> questions,
        Map<String, Object> runtimeQuiz,
        Map<String, Object> answerKey
) {

    public record CompiledSection(
            String sectionId,
            String title,
            int orderIndex
    ) {
    }

    public record CompiledQuestion(
            String runtimeQuestionId,
            String sourceSlideId,
            String sectionId,
            String bankQuestionId,
            String kind,
            String authoringType,
            java.math.BigDecimal points,
            boolean autoGradable,
            String gradingPolicy,
            int orderIndex,
            Map<String, Object> body,
            Map<String, Object> answerKey
    ) {
    }
}
