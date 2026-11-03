package com.erg.ergspringboot.service.quiz.publish;

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
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            putIfPresent(key, "order", firstNonNull(slide.get("correctOrder"), slide.get("order"), ids(slide.get("sequenceItems"))));
        }
    },

    MATCHING {
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

    private static String correctOptionId(Map<String, Object> slide) {
        Object explicit = firstNonNull(slide.get("correctChoiceId"), slide.get("correctAnswer"));
        if (explicit != null) {
            return explicit.toString();
        }
        Object options = slide.get("options");
        if (!(options instanceof List<?>)) {
            options = slide.get("choices");
        }
        if (options instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && boolValue(map.get("correct"))) {
                    Object id = map.get("id");
                    return id == null ? null : id.toString();
                }
            }
        }
        return null;
    }

    private static List<String> correctOptionIds(Map<String, Object> slide) {
        Object explicit = slide.get("correctChoiceIds");
        List<String> ids = new ArrayList<>();
        if (explicit instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    ids.add(item.toString());
                }
            }
            return ids;
        }
        Object options = slide.get("options");
        if (!(options instanceof List<?>)) {
            options = slide.get("choices");
        }
        if (options instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && boolValue(map.get("correct"))) {
                    Object id = map.get("id");
                    if (id != null) {
                        ids.add(id.toString());
                    }
                }
            }
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static List<String> ids(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object id = ((Map<String, Object>) map).get("id");
                    if (id != null) {
                        result.add(id.toString());
                    }
                } else if (item != null) {
                    result.add(item.toString());
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> matchingPairs(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> pair = (Map<String, Object>) map;
                    Object id = pair.get("id");
                    if (id != null) {
                        result.add(Map.of("promptId", id, "responseId", id));
                    }
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> blankAnswers(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> blank = (Map<String, Object>) map;
                    Map<String, Object> answer = new LinkedHashMap<>();
                    answer.put("id", blank.get("id"));
                    answer.put("answers", firstNonNull(blank.get("answers"), blank.get("answer")));
                    result.add(answer);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static List<Map<String, Object>> textBlankAnswers(Object value) {
        return idAnswers(value, "correctAnswers", false);
    }

    private static List<Map<String, Object>> inlineBlankAnswers(Object value) {
        return idAnswers(value, "correctOptionId", true);
    }

    private static List<Map<String, Object>> wordSlotAnswers(Object value) {
        return idAnswers(value, "correctWordId", true);
    }

    /** {@code [{id, answers}]} for items carrying both an id and the answer field; single answers are listed. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> idAnswers(Object value, String answerField, boolean singleAnswer) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> entry = (Map<String, Object>) map;
                    Object answer = entry.get(answerField);
                    if (entry.get("id") != null && answer != null) {
                        result.add(Map.of("id", entry.get("id"), "answers", singleAnswer ? List.of(answer) : answer));
                    }
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> correctHotspotAreas(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && boolValue(map.get("correct"))) {
                    result.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dragDropTargets(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> dragItem = (Map<String, Object>) map;
                    Object id = dragItem.get("id");
                    Object targetId = firstNonNull(dragItem.get("correctTargetId"), dragItem.get("target"));
                    if (id != null && targetId != null) {
                        result.add(Map.of("itemId", id, "targetId", targetId));
                    }
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static void putIfPresent(Map<String, Object> key, String field, Object value) {
        if (value != null) {
            key.put(field, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object mapValue(Object holder, String field) {
        if (holder instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).get(field);
        }
        return null;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean boolValue(Object value) {
        return value instanceof Boolean bool && bool;
    }
}
