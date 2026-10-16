package com.erg.ergspringboot.service.quiz.publish;

import java.util.Set;

/** Immutable build-time metadata for one question kind across all quiz phases. */
public record QuestionKindDescriptor(
        String runtimeKind,
        Set<String> authoringAliases,
        String gradingPolicy,
        boolean autoGradable,
        AnswerKeyRule answerKeyRule,
        GradingRule gradingRule
) {
    public enum AnswerKeyRule {
        EXACT_CHOICE,
        MULTIPLE_RESPONSE,
        SHORT_ANSWER,
        NUMERIC,
        SEQUENCE,
        MATCHING,
        BLANKS,
        HOTSPOT,
        DRAG_DROP,
        NONE
    }

    public enum GradingRule {
        EXACT_CHOICE,
        MULTIPLE_RESPONSE,
        SHORT_ANSWER,
        NUMERIC,
        SEQUENCE,
        PAIR_RATIO,
        BLANKS,
        HOTSPOT,
        SURVEY,
        MANUAL
    }
}
