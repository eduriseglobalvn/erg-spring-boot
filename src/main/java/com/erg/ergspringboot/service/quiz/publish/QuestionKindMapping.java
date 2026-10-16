package com.erg.ergspringboot.service.quiz.publish;

import java.util.Locale;

/**
 * Authoring-type -> runtime-kind mapping and grading policy (spec §3).
 *
 * <p>Authoring uses kebab-case types (from the FE editor); runtime uses snake_case kinds. Each mapping
 * also fixes the grading policy: most kinds are {@code auto}, {@code likert-scale} is {@code survey}
 * (scored 0, raw kept for distribution) and {@code essay} is {@code manual} (teacher-graded). Kinds are
 * stored as text so adding a 15th question type is a data change, not a schema change (spec §11.1).
 */
public final class QuestionKindMapping {

    public static final String POLICY_AUTO = "auto";
    public static final String POLICY_MANUAL = "manual";
    public static final String POLICY_SURVEY = "survey";

    /** Slide types that are not questions and drop out of the runtime question list (spec §3). */
    public static final java.util.Set<String> NON_QUESTION_SLIDE_TYPES = java.util.Set.of(
            "info-slide", "result-slide", "intro-slide", "user-info", "instruction-slide"
    );

    public record Mapping(String runtimeKind, String gradingPolicy, boolean autoGradable) {
    }

    private QuestionKindMapping() {
    }

    public static boolean isQuestionType(String authoringType) {
        String normalized = normalize(authoringType);
        return normalized != null
                && !NON_QUESTION_SLIDE_TYPES.contains(normalized)
                && QuestionKindRegistry.byAuthoringType(normalized).isPresent();
    }

    /** Returns the mapping for an authoring type, or {@code null} when the type is not a gradable question. */
    public static Mapping forAuthoringType(String authoringType) {
        return QuestionKindRegistry.byAuthoringType(authoringType)
                .map(descriptor -> new Mapping(descriptor.runtimeKind(), descriptor.gradingPolicy(), descriptor.autoGradable()))
                .orElse(null);
    }

    public static QuestionKindDescriptor requireRuntimeKind(String runtimeKind) {
        return QuestionKindRegistry.requireRuntimeKind(runtimeKind);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
