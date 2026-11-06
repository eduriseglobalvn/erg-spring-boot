package com.erg.ergspringboot.lcms.quiz.publish;

import static com.erg.ergspringboot.lcms.quiz.publish.AnswerKeySources.firstNonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads student answers and builds grades for {@link QuestionKindStrategy}. Mirrors the FE assessment engine
 * so a server recompute matches the client result: answers are accepted in their direct field, the FE
 * player alias, or nested under {@code answer}.
 */
final class AnswerGrading {

    private static final int SCALE = 2;

    private AnswerGrading() {
    }

    static GradeResult zero() {
        return new GradeResult(false, false, BigDecimal.ZERO, false);
    }

    static GradeResult allOrNothing(boolean correct, BigDecimal max) {
        return new GradeResult(correct, false, correct ? max : BigDecimal.ZERO, false);
    }

    static GradeResult ratioResult(long right, int total, BigDecimal max) {
        if (total == 0) {
            return zero();
        }
        boolean allCorrect = right == total;
        double ratio = (double) right / total;
        BigDecimal awarded = scaled(max, ratio);
        boolean partial = right > 0 && !allCorrect;
        return new GradeResult(allCorrect, partial, awarded, false);
    }

    static BigDecimal scaled(BigDecimal max, double ratio) {
        return max.multiply(BigDecimal.valueOf(ratio)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Shared by matching and drag-and-drop: per-pair ratio over the key's pairs (or targets). */
    static GradeResult pairRatio(Object rawAnswer, Map<String, Object> key, BigDecimal max) {
        List<Map<String, Object>> expected = mapList(key.get("pairs"));
        if (expected.isEmpty()) {
            expected = mapList(key.get("targets"));
        }
        Object pairs = answerValue(rawAnswer, "pairs");
        Object targets = answerValue(rawAnswer, "targets");
        Map<String, Object> actual = answerMap(pairs);
        if (actual.isEmpty()) {
            actual = answerMap(targets);
        }
        if (expected.isEmpty()) {
            return zero();
        }
        // Only an answer with no assignment map at all is a legacy row-order answer; an empty map means nothing connected.
        if (pairs == null && targets == null) {
            actual = positionalAssignments(rawAnswer, expected);
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

    /** Legacy FE answer {@code matchingOrder}: response ids listed in prompt (row) order. */
    private static Map<String, Object> positionalAssignments(Object rawAnswer, List<Map<String, Object>> expected) {
        List<String> order = stringList(answerValue(rawAnswer, "matchingOrder"));
        Map<String, Object> assignments = new LinkedHashMap<>();
        for (int i = 0; i < expected.size() && i < order.size(); i++) {
            Map<String, Object> pair = expected.get(i);
            String promptId = string(firstNonNull(pair.get("promptId"), pair.get("itemId"), pair.get("id")));
            if (promptId != null) {
                assignments.put(promptId, order.get(i));
            }
        }
        return assignments;
    }

    static boolean pointInRegion(Map<String, Object> region, double x, double y) {
        Double rx = doubleValue(region.get("x"));
        Double ry = doubleValue(region.get("y"));
        Double width = doubleValue(region.get("width"));
        Double height = doubleValue(region.get("height"));
        if (rx == null || ry == null || width == null || height == null) {
            return false;
        }
        if ("ellipse".equals(region.get("shape"))) {
            double radiusX = width / 2;
            double radiusY = height / 2;
            if (radiusX <= 0 || radiusY <= 0) {
                return false;
            }
            double nx = (x - rx - radiusX) / radiusX;
            double ny = (y - ry - radiusY) / radiusY;
            return nx * nx + ny * ny <= 1;
        }
        return x >= rx && x <= rx + width && y >= ry && y <= ry + height;
    }

    @SuppressWarnings("unchecked")
    static Object answerValue(Object rawAnswer, String field) {
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
    private static Object answerAlias(Map<String, Object> answer, String field) {
        return switch (field) {
            case "text" -> firstText(answer.get("textResponses"));
            case "value" -> answer.get("numericValue");
            case "order" -> answer.get("sequenceOrder");
            case "pairs" -> answer.get("matchingAssignments");
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
    private static Object firstText(Object value) {
        if (value instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).values().stream()
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> answerMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> mapList(Object value) {
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

    static List<String> stringList(Object value) {
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

    /**
     * Short answer: {@code correctAnswers}, else every blank's answers (the FE writes one textBlank per accepted
     * answer). Blank candidates are dropped so an empty response can never match them.
     */
    static List<String> acceptedAnswers(Map<String, Object> key) {
        List<String> accepted = stringList(key.get("correctAnswers"));
        if (accepted.isEmpty()) {
            for (Map<String, Object> blank : mapList(key.get("blanks"))) {
                accepted.addAll(stringList(blank.get("answers")));
            }
        }
        accepted.removeIf(candidate -> normalizeText(candidate).isEmpty());
        return accepted;
    }

    static String normalizeText(String value) {
        if (value == null) return "";
        // NFC so composed/decomposed Vietnamese diacritics compare equal; collapse internal whitespace runs.
        String composed = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC);
        return composed.strip().replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String string(Object value) {
        return value == null ? null : value.toString();
    }

    static Double doubleValue(Object value) {
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

    /** Validation helpers for {@link QuestionKindStrategy#missingAnswerKey}. */
    static boolean blank(Object value) {
        return value == null || value.toString().isBlank();
    }

    static boolean emptyList(Object value) {
        return !(value instanceof List<?> list) || list.isEmpty();
    }
}
