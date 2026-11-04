package com.erg.ergspringboot.lcms.quiz.publish;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable question-kind registry. It is deliberately a closed build-time list: adding a kind
 * requires one {@link QuestionKindStrategy} (strip, answer key, validation, grading) and its registration
 * line here instead of silently falling through.
 */
public final class QuestionKindRegistry {

    private static final List<QuestionKindDescriptor> DESCRIPTORS = List.of(
            descriptor("single_choice", Set.of("multiple-choice"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.EXACT_CHOICE),
            descriptor("multiple_response", Set.of("multiple-response"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.MULTIPLE_RESPONSE),
            descriptor("true_false", Set.of("true-false"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.EXACT_CHOICE),
            descriptor("short_answer", Set.of("short-answer"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.SHORT_ANSWER),
            descriptor("numeric", Set.of("numeric"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.NUMERIC),
            descriptor("sequence", Set.of("sequence"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.SEQUENCE),
            descriptor("matching", Set.of("matching"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.MATCHING),
            descriptor("fill_blank", Set.of("fill-in-the-blanks"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.BLANKS),
            descriptor("select_from_lists", Set.of("select-from-lists"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.BLANKS),
            descriptor("drag_words", Set.of("drag-the-words"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.BLANKS),
            descriptor("hotspot", Set.of("hotspot"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.HOTSPOT),
            descriptor("drag_drop", Set.of("drag-and-drop"), QuestionKindMapping.POLICY_AUTO, true,
                    QuestionKindStrategy.DRAG_DROP),
            descriptor("likert_scale", Set.of("likert-scale"), QuestionKindMapping.POLICY_SURVEY, false,
                    QuestionKindStrategy.SURVEY),
            descriptor("essay", Set.of("essay"), QuestionKindMapping.POLICY_MANUAL, false,
                    QuestionKindStrategy.MANUAL)
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
            boolean autoGradable, QuestionKindStrategy strategy) {
        return new QuestionKindDescriptor(runtimeKind, Set.copyOf(aliases), policy, autoGradable, strategy);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
