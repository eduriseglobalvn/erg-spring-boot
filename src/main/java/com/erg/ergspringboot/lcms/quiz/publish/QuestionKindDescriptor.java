package com.erg.ergspringboot.lcms.quiz.publish;

import java.util.Set;

/** Immutable build-time metadata for one question kind across all quiz phases. */
public record QuestionKindDescriptor(
        String runtimeKind,
        Set<String> authoringAliases,
        String gradingPolicy,
        boolean autoGradable,
        QuestionKindStrategy strategy
) {
}
