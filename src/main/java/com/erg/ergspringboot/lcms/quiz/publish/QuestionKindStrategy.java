package com.erg.ergspringboot.lcms.quiz.publish;

import static com.erg.ergspringboot.lcms.quiz.publish.AnswerGrading.*;
import static com.erg.ergspringboot.lcms.quiz.publish.AnswerKeySources.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-kind strategies, registered per runtime kind in {@link QuestionKindRegistry}. Each strategy owns every
 * kind-specific phase of its question family, so adding a kind is one constant here plus one registry line:
 * <ul>
 *   <li>{@link #stripAnswers}: which correctness markers are stripped from the player-facing body (§6);</li>
 *   <li>{@link #extractAnswerKey}: how the server-only answer key is extracted from the authoring slide (§3);</li>
 *   <li>{@link #missingAnswerKey}: the publish-gate check that the answer key is solvable (§8);</li>
 *   <li>{@link #grade}: server-side grading of one answer against that key (§6).</li>
 * </ul>
 *
 * <p>Stripping is defence in depth: {@link #stripAllAnswers} runs every strategy's strip on every body,
 * regardless of the slide's own kind, so a stray correctness field belonging to another kind can never
 * reach the player. Answer-key extraction, validation and grading only run the strategy of the slide's kind.
 */
public enum QuestionKindStrategy {

    /** single_choice and true_false: exact match on one choice id. */
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

        @Override
        String missingAnswerKey(Map<String, Object> key) {
            return blank(key.get("correctChoiceId")) ? "correct choice" : null;
        }

        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            String expected = string(key.get("correctChoiceId"));
            String actual = string(answerValue(rawAnswer, "choiceId"));
            return allOrNothing(expected != null && expected.equals(actual), max);
        }
    },

    /** Partial = (right - wrong) / total_correct floored at 0 when partial accepted, else all-or-nothing. */
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
                // The quiz editor stores the toggle under slide.options (a map; "options" can also be a choice list).
                Object editorAcceptPartial = slide.get("options") instanceof Map<?, ?> options ? options.get("acceptPartial") : null;
                key.put("acceptPartial", boolValue(firstNonNull(slide.get("acceptPartial"), editorAcceptPartial)));
            }
        }

        @Override
        String missingAnswerKey(Map<String, Object> key) {
            return emptyList(key.get("correctChoiceIds")) ? "at least one correct choice" : null;
        }

        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            Set<String> correct = new LinkedHashSet<>(stringList(key.get("correctChoiceIds")));
            Set<String> chosen = new LinkedHashSet<>(stringList(answerValue(rawAnswer, "choiceIds")));
            if (correct.isEmpty()) {
                return zero();
            }
            boolean acceptPartial = boolValue(key.get("acceptPartial"));
            long right = chosen.stream().filter(correct::contains).count();
            long wrong = chosen.stream().filter(id -> !correct.contains(id)).count();
            boolean allCorrect = right == correct.size() && wrong == 0;
            if (!acceptPartial) {
                return allOrNothing(allCorrect, max);
            }
            double ratio = Math.max(0.0, (double) (right - wrong) / correct.size());
            boolean partial = ratio > 0 && !allCorrect;
            return new GradeResult(allCorrect, partial, scaled(max, ratio), false);
        }
    },

    /** NFC-normalized, whitespace-collapsed, case-insensitive match against any accepted answer (or runtime textBlanks). */
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

        @Override
        String missingAnswerKey(Map<String, Object> key) {
            return acceptedAnswers(key).isEmpty() ? "accepted answers" : null;
        }

        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            List<String> accepted = acceptedAnswers(key);
            String actual = normalizeText(string(answerValue(rawAnswer, "text")));
            return allOrNothing(accepted.stream().anyMatch(candidate -> normalizeText(candidate).equals(actual)), max);
        }
    },

    /** Within absolute tolerance (0 when absent). */
    NUMERIC {
        @Override
        @SuppressWarnings("unchecked")
        void stripAnswers(Map<String, Object> body) {
            body.keySet().removeAll(List.of("correctAnswer", "value", "tolerance"));
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

        @Override
        String missingAnswerKey(Map<String, Object> key) {
            return key.get("value") == null ? "numeric value" : null;
        }

        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            Double expected = doubleValue(key.get("value"));
            Double tolerance = doubleValue(key.get("tolerance"));
            Double actual = doubleValue(answerValue(rawAnswer, "value"));
            if (expected == null || actual == null) {
                return zero();
            }
            double allowed = tolerance == null ? 0.0 : Math.abs(tolerance);
            return allOrNothing(Math.abs(actual - expected) <= allowed, max);
        }
    },

    /** Per-position ratio. */
    SEQUENCE {
        @Override
        void stripAnswers(Map<String, Object> body) {
            body.keySet().removeAll(List.of("correctOrder", "order", "sequenceCorrectOrder"));
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            putIfPresent(key, "order", firstNonNull(slide.get("correctOrder"), slide.get("order"), ids(slide.get("sequenceItems"))));
        }

        @Override
        String missingAnswerKey(Map<String, Object> key) {
            return emptyList(key.get("order")) ? "correct order" : null;
        }

        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            List<String> expected = stringList(key.get("order"));
            List<String> actual = stringList(answerValue(rawAnswer, "order"));
            if (expected.isEmpty()) {
                return zero();
            }
            long rightPositions = 0;
            for (int i = 0; i < expected.size(); i++) {
                if (i < actual.size() && expected.get(i).equals(actual.get(i))) {
                    rightPositions++;
                }
            }
            return ratioResult(rightPositions, expected.size(), max);
        }
    },

    /** Per-pair ratio. */
    MATCHING {
        @Override
        void stripAnswers(Map<String, Object> body) {
            body.keySet().removeAll(List.of("correctPairs", "pairs", "matchingKey"));
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            putIfPresent(key, "pairs", firstNonNull(slide.get("correctPairs"), slide.get("pairs"), matchingPairs(slide.get("matching"))));
        }

        @Override
        String missingAnswerKey(Map<String, Object> key) {
            return emptyList(key.get("pairs")) ? "matching pairs" : null;
        }

        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            return pairRatio(rawAnswer, key, max);
        }
    },

    /** fill_blank, select_from_lists, drag_words: per-blank normalized ratio. */
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

        @Override
        String missingAnswerKey(Map<String, Object> key) {
            return emptyList(key.get("blanks")) ? "blank answers" : null;
        }

        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            List<Map<String, Object>> expected = mapList(key.get("blanks"));
            Map<String, Object> actual = answerMap(answerValue(rawAnswer, "blanks"));
            if (expected.isEmpty()) {
                return zero();
            }
            long right = 0;
            for (Map<String, Object> blank : expected) {
                String id = string(blank.get("id"));
                List<String> accepted = stringList(blank.get("answers"));
                String given = normalizeText(string(actual.get(id)));
                if (accepted.stream().anyMatch(candidate -> normalizeText(candidate).equals(given))) {
                    right++;
                }
            }
            return ratioResult(right, expected.size(), max);
        }
    },

    /** Point-in-any-region. */
    HOTSPOT {
        @Override
        void stripAnswers(Map<String, Object> body) {
            // Every drawn area is a correct one, so its geometry is the answer: the learner gets the image only,
            // and review/training read the areas from the answer key (regions).
            body.keySet().removeAll(List.of("hotspotAreas", "correctRegions", "regions"));
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            putIfPresent(key, "regions", firstNonNull(slide.get("correctRegions"), slide.get("regions"), correctHotspotAreas(slide.get("hotspotAreas"))));
        }

        @Override
        String missingAnswerKey(Map<String, Object> key) {
            return emptyList(key.get("regions")) ? "correct region" : null;
        }

        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            List<Map<String, Object>> regions = mapList(key.get("regions"));
            Double x = doubleValue(answerValue(rawAnswer, "x"));
            Double y = doubleValue(answerValue(rawAnswer, "y"));
            if (regions.isEmpty() || x == null || y == null) {
                return zero();
            }
            for (Map<String, Object> region : regions) {
                if (pointInRegion(region, x, y)) {
                    return new GradeResult(true, false, max, false);
                }
            }
            return zero();
        }
    },

    /** Per-item ratio, graded like matching. An editor item's {@code target} is its group label: the answer. */
    DRAG_DROP {
        @Override
        void stripAnswers(Map<String, Object> body) {
            stripListItems(body, "dragDropItems", "correctTargetId", "target", "targetMedia", "emoji");
            body.remove("correctTargets");
            body.remove("targets");
        }

        @Override
        void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
            putIfPresent(key, "targets", firstNonNull(slide.get("correctTargets"), slide.get("targets"), dragDropTargets(slide)));
        }

        @Override
        String missingAnswerKey(Map<String, Object> key) {
            return emptyList(key.get("targets")) ? "item targets" : null;
        }

        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            return pairRatio(rawAnswer, key, max);
        }
    },

    /** likert_scale: survey, no answer key, scored 0 (raw kept for distribution). */
    SURVEY {
        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            return new GradeResult(null, false, BigDecimal.ZERO, false);
        }
    },

    /** essay: no answer key, teacher-graded (deferred); the marking guide is for teachers only (spec §7). */
    MANUAL {
        @Override
        void stripAnswers(Map<String, Object> body) {
            body.remove("essayRubric");
        }

        @Override
        GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
            return new GradeResult(null, false, BigDecimal.ZERO, true);
        }
    };

    /** Removes this kind's correctness markers from a player-facing body. Default: nothing to strip. */
    void stripAnswers(Map<String, Object> body) {
    }

    /** Copies this kind's correctness data from the authoring slide into the server-only answer key. */
    void extractAnswerKey(Map<String, Object> slide, Map<String, Object> key) {
    }

    /** Publish gate: what the compiled answer key is missing, or {@code null} when solvable. Default: no key needed. */
    String missingAnswerKey(Map<String, Object> key) {
        return null;
    }

    abstract GradeResult gradeAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max);

    /** Grades one raw student answer; a null key grades against an empty key and null max points as zero. */
    public GradeResult grade(Object rawAnswer, Map<String, Object> answerKey, BigDecimal maxPoints) {
        return gradeAnswer(rawAnswer, answerKey == null ? Map.of() : answerKey,
                maxPoints == null ? BigDecimal.ZERO : maxPoints);
    }

    static QuestionKindStrategy forRuntimeKind(String runtimeKind) {
        return QuestionKindRegistry.requireRuntimeKind(runtimeKind).strategy();
    }

    /** Pulls the correctness data for a slide into the server-only answer key (§3 answer-key split). */
    static Map<String, Object> answerKeyFor(Map<String, Object> slide, String runtimeKind) {
        Map<String, Object> key = new LinkedHashMap<>();
        forRuntimeKind(runtimeKind).extractAnswerKey(slide, key);
        return key;
    }

    /** Strips the correctness markers of every kind so the player-facing body never leaks answers (§6). */
    static void stripAllAnswers(Map<String, Object> body) {
        for (QuestionKindStrategy strategy : values()) {
            strategy.stripAnswers(body);
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
