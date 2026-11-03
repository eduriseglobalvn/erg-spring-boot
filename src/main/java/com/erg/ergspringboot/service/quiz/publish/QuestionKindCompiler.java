package com.erg.ergspringboot.service.quiz.publish;

import static com.erg.ergspringboot.service.quiz.publish.AnswerKeySources.*;

import com.erg.ergspringboot.service.quiz.publish.QuestionKindDescriptor.AnswerKeyRule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-kind compile strategies, one per {@link AnswerKeyRule} of the {@link QuestionKindRegistry}.
 *
 * <p>Each strategy owns two things for its kind family: which correctness markers are stripped from the
 * player-facing body (§6) and how the server-only answer key is extracted from the authoring slide (§3).
 *
 * <p>Stripping is defence in depth: {@link #stripAllAnswers} runs every strategy's strip on every body,
 * regardless of the slide's own kind, so a stray correctness field belonging to another kind can never
 * reach the player. Answer-key extraction only runs the strategy of the slide's kind.
 */
enum QuestionKindCompiler {

    EXACT_CHOICE {
        @Override
        void stripAnswers(Map<String, Object> body) {
            stripChoiceCorrectness(body);
            body.remove("correctChoiceId");
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            String correct = correctOptionId(slide);
            if (correct != null) {
                key.put("correctChoiceId", correct);
            }
        }
    },

    MULTIPLE_RESPONSE {
        @Override
        void stripAnswers(Map<String, Object> body) {
            stripChoiceCorrectness(body);
            body.remove("correctChoiceIds");
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            List<String> correct = correctOptionIds(slide);
            if (!correct.isEmpty()) {
                key.put("correctChoiceIds", correct);
                key.put("acceptPartial", boolValue(slide.get("acceptPartial")));
            }
        }
    },

    SHORT_ANSWER {
        @Override
        void stripAnswers(Map<String, Object> body) {
            body.remove("correctAnswers");
            stripListItems(body, "textBlanks", "correctAnswers");
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            putIfPresent(key, "correctAnswers", slide.get("correctAnswers"));
            putIfPresent(key, "blanks", textBlankAnswers(slide.get("textBlanks")));
        }
    },

    NUMERIC {
        @Override
        @SuppressWarnings("unchecked")
        void stripAnswers(Map<String, Object> body) {
            body.remove("correctAnswer");
            body.remove("tolerance");
            Object numericAnswer = body.get("numericAnswer");
            if (numericAnswer instanceof Map<?, ?> map) {
                Map<String, Object> stripped = new LinkedHashMap<>((Map<String, Object>) map);
                stripped.remove("correctValue");
                stripped.remove("tolerance");
                body.put("numericAnswer", stripped);
            }
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            Object numericAnswer = slide.get("numericAnswer");
            putIfPresent(key, "value", firstNonNull(slide.get("correctAnswer"), slide.get("value"), mapValue(numericAnswer, "correctValue")));
            putIfPresent(key, "tolerance", firstNonNull(slide.get("tolerance"), mapValue(numericAnswer, "tolerance")));
        }
    },

    SEQUENCE {
        @Override
        void stripAnswers(Map<String, Object> body) {
            body.remove("correctOrder");
            body.remove("order");
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            putIfPresent(key, "order", firstNonNull(slide.get("correctOrder"), slide.get("order"), ids(slide.get("sequenceItems"))));
        }
    },

    MATCHING {
        @Override
        void stripAnswers(Map<String, Object> body) {
            body.remove("correctPairs");
            body.remove("pairs");
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            putIfPresent(key, "pairs", firstNonNull(slide.get("correctPairs"), slide.get("pairs"), matchingPairs(slide.get("matching"))));
        }
    },

    BLANKS {
        @Override
        void stripAnswers(Map<String, Object> body) {
            stripListItems(body, "blanks", "answers", "answer", "correct");
            stripListItems(body, "inlineBlanks", "correctOptionId");
            stripListItems(body, "wordSlots", "correctWordId");
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            Object answers = firstNonNull(blankAnswers(slide.get("blanks")), textBlankAnswers(slide.get("textBlanks")),
                    inlineBlankAnswers(slide.get("inlineBlanks")), wordSlotAnswers(slide.get("wordSlots")));
            putIfPresent(key, "blanks", answers);
        }
    },

    HOTSPOT {
        @Override
        void stripAnswers(Map<String, Object> body) {
            stripListItems(body, "hotspotAreas", "correct");
            body.remove("correctRegions");
            body.remove("regions");
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            putIfPresent(key, "regions", firstNonNull(slide.get("correctRegions"), slide.get("regions"), correctHotspotAreas(slide.get("hotspotAreas"))));
        }
    },

    DRAG_DROP {
        @Override
        void stripAnswers(Map<String, Object> body) {
            stripListItems(body, "dragDropItems", "correctTargetId");
            body.remove("correctTargets");
            body.remove("targets");
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            putIfPresent(key, "targets", firstNonNull(slide.get("correctTargets"), slide.get("targets"), dragDropTargets(slide.get("dragDropItems"))));
        }
    },

    /** Essay and likert have no answer key. */
    NONE;

    /** Removes this kind's correctness markers from a player-facing body. Default: nothing to strip. */
    void stripAnswers(Map<String, Object> body) {
    }

    /** Copies this kind's correctness data from the authoring slide into the server-only answer key. */
    void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
    }

    /** Exhaustive mapping from the registry's answer-key rule: adding a rule fails compilation here. */
    static QuestionKindCompiler forRule(AnswerKeyRule rule) {
        return switch (rule) {
            case EXACT_CHOICE -> EXACT_CHOICE;
            case MULTIPLE_RESPONSE -> MULTIPLE_RESPONSE;
            case SHORT_ANSWER -> SHORT_ANSWER;
            case NUMERIC -> NUMERIC;
            case SEQUENCE -> SEQUENCE;
            case MATCHING -> MATCHING;
            case BLANKS -> BLANKS;
            case HOTSPOT -> HOTSPOT;
            case DRAG_DROP -> DRAG_DROP;
            case NONE -> NONE;
        };
    }

    static QuestionKindCompiler forRuntimeKind(String runtimeKind) {
        return forRule(QuestionKindRegistry.requireRuntimeKind(runtimeKind).answerKeyRule());
    }

    /** Pulls the correctness data for a slide into the server-only answer key (§3 answer-key split). */
    static Map<String, Object> answerKeyFor(Map<String, Object> slide, String runtimeKind) {
        Map<String, Object> key = new LinkedHashMap<>();
        forRuntimeKind(runtimeKind).extractAnswerKey(slide, key);
        return key;
    }

    /** Strips the correctness markers of every kind so the player-facing body never leaks answers (§6). */
    static void stripAllAnswers(Map<String, Object> body) {
        for (QuestionKindCompiler compiler : values()) {
            compiler.stripAnswers(body);
        }
    }

    // Idempotent: both choice kinds share the option lists, so running it twice yields the same body.
    private static void stripChoiceCorrectness(Map<String, Object> body) {
        stripListItems(body, "options", "correct", "isCorrect");
        stripListItems(body, "choices", "correct", "isCorrect");
    }

    /** Replaces a list field with copies of its map items minus the given markers; non-map items drop out. */
    @SuppressWarnings("unchecked")
    private static void stripListItems(Map<String, Object> body, String field, String... markers) {
        Object value = body.get(field);
        if (value instanceof List<?> list) {
            List<Map<String, Object>> stripped = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
                    for (String marker : markers) {
                        copy.remove(marker);
                    }
                    stripped.add(copy);
                }
            }
            body.put(field, stripped);
        }
    }
}
