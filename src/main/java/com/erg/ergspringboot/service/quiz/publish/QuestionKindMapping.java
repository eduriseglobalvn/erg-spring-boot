package com.erg.ergspringboot.service.quiz.publish;

import java.util.Locale;
import java.util.Map;

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

    private static final Map<String, Mapping> BY_AUTHORING_TYPE = Map.ofEntries(
            Map.entry("multiple-choice", new Mapping("single_choice", POLICY_AUTO, true)),
            Map.entry("multiple-response", new Mapping("multiple_response", POLICY_AUTO, true)),
            Map.entry("true-false", new Mapping("true_false", POLICY_AUTO, true)),
            Map.entry("short-answer", new Mapping("short_answer", POLICY_AUTO, true)),
            Map.entry("numeric", new Mapping("numeric", POLICY_AUTO, true)),
            Map.entry("sequence", new Mapping("sequence", POLICY_AUTO, true)),
            Map.entry("matching", new Mapping("matching", POLICY_AUTO, true)),
            Map.entry("fill-in-the-blanks", new Mapping("fill_blank", POLICY_AUTO, true)),
            Map.entry("select-from-lists", new Mapping("select_from_lists", POLICY_AUTO, true)),
            Map.entry("drag-the-words", new Mapping("drag_words", POLICY_AUTO, true)),
            Map.entry("hotspot", new Mapping("hotspot", POLICY_AUTO, true)),
            Map.entry("drag-and-drop", new Mapping("drag_drop", POLICY_AUTO, true)),
            Map.entry("likert-scale", new Mapping("likert_scale", POLICY_SURVEY, false)),
            Map.entry("essay", new Mapping("essay", POLICY_MANUAL, false))
    );

    private QuestionKindMapping() {
    }

    public static boolean isQuestionType(String authoringType) {
        String normalized = normalize(authoringType);
        return normalized != null
                && !NON_QUESTION_SLIDE_TYPES.contains(normalized)
                && BY_AUTHORING_TYPE.containsKey(normalized);
    }

    /** Returns the mapping for an authoring type, or {@code null} when the type is not a gradable question. */
    public static Mapping forAuthoringType(String authoringType) {
        return BY_AUTHORING_TYPE.get(normalize(authoringType));
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
