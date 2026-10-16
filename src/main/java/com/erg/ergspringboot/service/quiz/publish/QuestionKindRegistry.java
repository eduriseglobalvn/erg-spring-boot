package com.erg.ergspringboot.service.quiz.publish;

import com.erg.ergspringboot.service.quiz.publish.QuestionKindDescriptor.AnswerKeyRule;
import com.erg.ergspringboot.service.quiz.publish.QuestionKindDescriptor.GradingRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable question-kind registry. It is deliberately a closed build-time list: adding a kind
 * requires an explicit mapping, answer-key rule and grader rule instead of silently falling through.
 */
public final class QuestionKindRegistry {

    private static final List<QuestionKindDescriptor> DESCRIPTORS = List.of(
            descriptor("single_choice", Set.of("multiple-choice"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.EXACT_CHOICE, GradingRule.EXACT_CHOICE),
            descriptor("multiple_response", Set.of("multiple-response"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.MULTIPLE_RESPONSE, GradingRule.MULTIPLE_RESPONSE),
            descriptor("true_false", Set.of("true-false"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.EXACT_CHOICE, GradingRule.EXACT_CHOICE),
            descriptor("short_answer", Set.of("short-answer"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.SHORT_ANSWER, GradingRule.SHORT_ANSWER),
            descriptor("numeric", Set.of("numeric"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.NUMERIC, GradingRule.NUMERIC),
            descriptor("sequence", Set.of("sequence"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.SEQUENCE, GradingRule.SEQUENCE),
            descriptor("matching", Set.of("matching"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.MATCHING, GradingRule.PAIR_RATIO),
            descriptor("fill_blank", Set.of("fill-in-the-blanks"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.BLANKS, GradingRule.BLANKS),
            descriptor("select_from_lists", Set.of("select-from-lists"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.BLANKS, GradingRule.BLANKS),
            descriptor("drag_words", Set.of("drag-the-words"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.BLANKS, GradingRule.BLANKS),
            descriptor("hotspot", Set.of("hotspot"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.HOTSPOT, GradingRule.HOTSPOT),
            descriptor("drag_drop", Set.of("drag-and-drop"), QuestionKindMapping.POLICY_AUTO, true,
                    AnswerKeyRule.DRAG_DROP, GradingRule.PAIR_RATIO),
            descriptor("likert_scale", Set.of("likert-scale"), QuestionKindMapping.POLICY_SURVEY, false,
                    AnswerKeyRule.NONE, GradingRule.SURVEY),
            descriptor("essay", Set.of("essay"), QuestionKindMapping.POLICY_MANUAL, false,
                    AnswerKeyRule.NONE, GradingRule.MANUAL)
    );

    private static final Map<String, QuestionKindDescriptor> BY_RUNTIME;
    private static final Map<String, QuestionKindDescriptor> BY_AUTHORING;

    static {
        Map<String, QuestionKindDescriptor> runtime = new LinkedHashMap<>();
        Map<String, QuestionKindDescriptor> authoring = new LinkedHashMap<>();
        for (QuestionKindDescriptor descriptor : DESCRIPTORS) {
            if (runtime.put(descriptor.runtimeKind(), descriptor) != null) {
                throw new ExceptionInInitializerError("duplicate runtime question kind: " + descriptor.runtimeKind());
            }
            for (String alias : descriptor.authoringAliases()) {
                if (authoring.put(normalize(alias), descriptor) != null) {
                    throw new ExceptionInInitializerError("duplicate authoring question kind: " + alias);
                }
            }
        }
        BY_RUNTIME = Map.copyOf(runtime);
        BY_AUTHORING = Map.copyOf(authoring);
    }

    private QuestionKindRegistry() { }

    public static List<QuestionKindDescriptor> all() {
        return DESCRIPTORS;
    }

    public static Optional<QuestionKindDescriptor> byRuntimeKind(String runtimeKind) {
        return Optional.ofNullable(runtimeKind == null ? null : BY_RUNTIME.get(normalize(runtimeKind)));
    }

    public static Optional<QuestionKindDescriptor> byAuthoringType(String authoringType) {
        return Optional.ofNullable(authoringType == null ? null : BY_AUTHORING.get(normalize(authoringType)));
    }

    public static QuestionKindDescriptor requireRuntimeKind(String runtimeKind) {
        return byRuntimeKind(runtimeKind).orElseThrow(() ->
                new IllegalArgumentException("unsupported question kind: " + runtimeKind));
    }

    private static QuestionKindDescriptor descriptor(String runtimeKind, Set<String> aliases, String policy,
            boolean autoGradable, AnswerKeyRule answerKeyRule, GradingRule gradingRule) {
        return new QuestionKindDescriptor(runtimeKind, Set.copyOf(aliases), policy, autoGradable, answerKeyRule, gradingRule);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
