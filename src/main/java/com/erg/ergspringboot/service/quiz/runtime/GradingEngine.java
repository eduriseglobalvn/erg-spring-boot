package com.erg.ergspringboot.service.quiz.runtime;

import com.erg.ergspringboot.service.quiz.publish.QuestionKindDescriptor.GradingRule;
import com.erg.ergspringboot.service.quiz.publish.QuestionKindRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Server-side grading of a single answer against its answer key (spec §6). Mirrors the FE assessment
 * engine so a server recompute matches the client result. Pure and deterministic: takes the runtime
 * kind, the raw student answer, the question's answer key and max points, and returns a graded result.
 *
 * <p>Per-kind rules (spec §6): single/true_false exact; multiple_response partial = (right - wrong) /
 * total_correct floored at 0 when partial accepted, else all-or-nothing; matching/drag/inline per-item
 * ratio; sequence per-position ratio; short_answer normalized match; numeric within tolerance; hotspot
 * point-in-region; likert survey (0 points); essay manual (deferred).
 */
@Component
public class GradingEngine {

    public record GradeResult(
            Boolean correct,
            boolean partiallyRight,
            BigDecimal awardedPoints,
            boolean needsManualGrade
    ) {
    }

    private static final int SCALE = 2;
    private interface Grader {
        GradeResult apply(Object rawAnswer, Map<String, Object> answerKey, BigDecimal maxPoints);
    }

    private final Map<GradingRule, Grader> graders;

    public GradingEngine() {
        graders = Map.of(
                GradingRule.EXACT_CHOICE, this::exactChoice,
                GradingRule.MULTIPLE_RESPONSE, this::multipleResponse,
                GradingRule.SHORT_ANSWER, this::shortAnswer,
                GradingRule.NUMERIC, this::numeric,
                GradingRule.SEQUENCE, this::sequence,
                GradingRule.PAIR_RATIO, this::pairRatio,
                GradingRule.BLANKS, this::blanks,
                GradingRule.HOTSPOT, this::hotspot,
                GradingRule.SURVEY, (raw, key, max) -> new GradeResult(null, false, BigDecimal.ZERO, false),
                GradingRule.MANUAL, (raw, key, max) -> new GradeResult(null, false, BigDecimal.ZERO, true)
        );
    }

    @SuppressWarnings("unchecked")
    public GradeResult grade(String kind, Object rawAnswer, Map<String, Object> answerKey, BigDecimal maxPoints) {
        BigDecimal max = maxPoints == null ? BigDecimal.ZERO : maxPoints;
        Map<String, Object> key = answerKey == null ? Map.of() : answerKey;
        GradingRule rule = QuestionKindRegistry.requireRuntimeKind(kind).gradingRule();
        Grader grader = graders.get(rule);
        if (grader == null) {
            throw new IllegalStateException("question kind has no grader rule: " + kind);
        }
        return grader.apply(rawAnswer, key, max);
    }

    private GradeResult exactChoice(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
        String expected = string(key.get("correctChoiceId"));
        String actual = string(answerValue(rawAnswer, "choiceId"));
        boolean correct = expected != null && expected.equals(actual);
        return new GradeResult(correct, false, correct ? max : BigDecimal.ZERO, false);
    }

    private GradeResult multipleResponse(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
        Set<String> correct = new LinkedHashSet<>(stringList(key.get("correctChoiceIds")));
        Set<String> chosen = new LinkedHashSet<>(stringList(answerValue(rawAnswer, "choiceIds")));
        if (correct.isEmpty()) {
            return new GradeResult(false, false, BigDecimal.ZERO, false);
        }
        boolean acceptPartial = boolValue(key.get("acceptPartial"));
        long right = chosen.stream().filter(correct::contains).count();
        long wrong = chosen.stream().filter(id -> !correct.contains(id)).count();
        boolean allCorrect = right == correct.size() && wrong == 0;
        if (!acceptPartial) {
            return new GradeResult(allCorrect, false, allCorrect ? max : BigDecimal.ZERO, false);
        }
        double ratio = Math.max(0.0, (double) (right - wrong) / correct.size());
        BigDecimal awarded = max.multiply(BigDecimal.valueOf(ratio)).setScale(SCALE, RoundingMode.HALF_UP);
        boolean partial = ratio > 0 && !allCorrect;
        return new GradeResult(allCorrect, partial, awarded, false);
    }

    private GradeResult shortAnswer(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
        List<String> accepted = stringList(key.get("correctAnswers"));
        if (accepted.isEmpty()) {
            accepted = firstBlankAnswers(key.get("blanks"));
        }
        String actual = normalizeText(string(answerValue(rawAnswer, "text")));
        boolean correct = accepted.stream().anyMatch(candidate -> normalizeText(candidate).equals(actual));
        return new GradeResult(correct, false, correct ? max : BigDecimal.ZERO, false);
    }

    private GradeResult numeric(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
        Double expected = doubleValue(key.get("value"));
        Double tolerance = doubleValue(key.get("tolerance"));
        Double actual = doubleValue(answerValue(rawAnswer, "value"));
        if (expected == null || actual == null) {
            return new GradeResult(false, false, BigDecimal.ZERO, false);
        }
        double allowed = tolerance == null ? 0.0 : Math.abs(tolerance);
        boolean correct = Math.abs(actual - expected) <= allowed;
        return new GradeResult(correct, false, correct ? max : BigDecimal.ZERO, false);
    }

    private GradeResult sequence(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
        List<String> expected = stringList(key.get("order"));
        List<String> actual = stringList(answerValue(rawAnswer, "order"));
        if (expected.isEmpty()) {
            return new GradeResult(false, false, BigDecimal.ZERO, false);
        }
        long rightPositions = 0;
        for (int i = 0; i < expected.size(); i++) {
            if (i < actual.size() && expected.get(i).equals(actual.get(i))) {
                rightPositions++;
            }
        }
        return ratioResult(rightPositions, expected.size(), max);
    }

    @SuppressWarnings("unchecked")
    private GradeResult pairRatio(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
        List<Map<String, Object>> expected = mapList(key.get("pairs"));
        if (expected.isEmpty()) {
            expected = mapList(key.get("targets"));
        }
        Map<String, Object> actual = answerMap(answerValue(rawAnswer, "pairs"));
        if (actual.isEmpty()) {
            actual = answerMap(answerValue(rawAnswer, "targets"));
        }
        if (expected.isEmpty()) {
            return new GradeResult(false, false, BigDecimal.ZERO, false);
        }
        long right = 0;
        for (Map<String, Object> pair : expected) {
            String promptId = string(firstNonNull(pair.get("promptId"), pair.get("itemId"), pair.get("id")));
            String responseId = string(firstNonNull(pair.get("responseId"), pair.get("targetId"), pair.get("value")));
            if (promptId != null && responseId != null && responseId.equals(string(actual.get(promptId)))) {
                right++;
            }
        }
        return ratioResult(right, expected.size(), max);
    }

    @SuppressWarnings("unchecked")
    private GradeResult blanks(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
        List<Map<String, Object>> expected = mapList(key.get("blanks"));
        Map<String, Object> actual = answerMap(answerValue(rawAnswer, "blanks"));
        if (expected.isEmpty()) {
            return new GradeResult(false, false, BigDecimal.ZERO, false);
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

    @SuppressWarnings("unchecked")
    private GradeResult hotspot(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
        List<Map<String, Object>> regions = mapList(key.get("regions"));
        Double x = doubleValue(answerValue(rawAnswer, "x"));
        Double y = doubleValue(answerValue(rawAnswer, "y"));
        if (regions.isEmpty() || x == null || y == null) {
            return new GradeResult(false, false, BigDecimal.ZERO, false);
        }
        for (Map<String, Object> region : regions) {
            if (pointInRegion(region, x, y)) {
                return new GradeResult(true, false, max, false);
            }
        }
        return new GradeResult(false, false, BigDecimal.ZERO, false);
    }

    private boolean pointInRegion(Map<String, Object> region, double x, double y) {
        Double rx = doubleValue(region.get("x"));
        Double ry = doubleValue(region.get("y"));
        Double width = doubleValue(region.get("width"));
        Double height = doubleValue(region.get("height"));
        if (rx == null || ry == null || width == null || height == null) {
            return false;
        }
        return x >= rx && x <= rx + width && y >= ry && y <= ry + height;
    }

    private GradeResult ratioResult(long right, int total, BigDecimal max) {
        if (total == 0) {
            return new GradeResult(false, false, BigDecimal.ZERO, false);
        }
        boolean allCorrect = right == total;
        double ratio = (double) right / total;
        BigDecimal awarded = max.multiply(BigDecimal.valueOf(ratio)).setScale(SCALE, RoundingMode.HALF_UP);
        boolean partial = right > 0 && !allCorrect;
        return new GradeResult(allCorrect, partial, awarded, false);
    }

    @SuppressWarnings("unchecked")
    private Object answerValue(Object rawAnswer, String field) {
        if (rawAnswer instanceof Map<?, ?> map) {
            Map<String, Object> answer = (Map<String, Object>) map;
            Object direct = answer.get(field);
            if (direct != null) {
                return direct;
            }
            Object alias = answerAlias(answer, field);
            if (alias != null) {
                return alias;
            }
            Object nested = answer.get("answer");
            if (nested instanceof Map<?, ?> inner) {
                Map<String, Object> nestedAnswer = (Map<String, Object>) inner;
                Object nestedDirect = nestedAnswer.get(field);
                return nestedDirect == null ? answerAlias(nestedAnswer, field) : nestedDirect;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object answerAlias(Map<String, Object> answer, String field) {
        return switch (field) {
            case "text" -> firstText(answer.get("textResponses"));
            case "value" -> answer.get("numericValue");
            case "order" -> answer.get("sequenceOrder");
            case "pairs" -> {
                Object assignments = answer.get("matchingAssignments");
                if (assignments != null) {
                    yield assignments;
                }
                Object order = answer.get("matchingOrder");
                if (order instanceof List<?> list) {
                    Map<String, Object> orderMap = new java.util.LinkedHashMap<>();
                    for (Object item : list) {
                        if (item != null) {
                            orderMap.put(item.toString(), item.toString());
                        }
                    }
                    yield orderMap;
                }
                yield null;
            }
            case "targets" -> answer.get("dragDropPlacements");
            case "blanks" -> firstNonNull(
                    answer.get("textResponses"),
                    answer.get("inlineSelections"),
                    answer.get("dragWordPlacements"));
            case "x", "y" -> {
                Object point = answer.get("hotspotPoint");
                if (point instanceof Map<?, ?> pointMap) {
                    yield ((Map<String, Object>) pointMap).get(field);
                }
                yield null;
            }
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private Object firstText(Object value) {
        if (value instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).values().stream()
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> answerMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
        }
        return result;
    }

    private List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        }
        return result;
    }

    private List<String> firstBlankAnswers(Object value) {
        List<Map<String, Object>> blanks = mapList(value);
        if (blanks.isEmpty()) {
            return List.of();
        }
        return stringList(blanks.get(0).get("answers"));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean boolValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
