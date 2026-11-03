package com.erg.ergspringboot.service.quiz.publish;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Builds the player-facing body of one runtime question from its authoring slide (spec §3): flattens the
 * editor's {@code runtimeQuestion}, drops presentation-only fields, maps editor media, strips correctness
 * markers through {@link QuestionKindCompiler} and merges the layered question settings.
 *
 * <p>Deterministic and side-effect free; the input slide is never mutated.
 */
final class QuestionBodyCompiler {

    // Presentation-only slide fields that never affect grading and are dropped from runtime body (§3).
    private static final Set<String> PRESENTATION_FIELDS = Set.of(
            "textStyles", "elementOffsets", "layoutPreset", "animationPreset", "themeColor",
            "layers", "infoPage", "media", "runtimeQuestion"
    );

    /**
     * The React editor keeps the canonical learner model in runtimeQuestion and
     * adds authoring-only fields beside it. Publishing must flatten that model;
     * otherwise advanced kinds look correct in preview but lose their runtime
     * arrays when serialized.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> mergeRuntimeQuestion(Map<String, Object> slide) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Object runtimeQuestion = slide.get("runtimeQuestion");
        if (runtimeQuestion instanceof Map<?, ?> map) {
            merged.putAll((Map<String, Object>) map);
        }
        merged.putAll(slide);
        return merged;
    }

    /** Copies the (merged) slide into a runtime body, dropping presentation fields and correctness markers. */
    Map<String, Object> compileBody(
            Map<String, Object> slide,
            String runtimeKind,
            String bankQuestionId,
            Map<String, Map<String, Object>> questionSettings,
            Map<String, Map<String, Object>> questionSetSettings
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : slide.entrySet()) {
            if (PRESENTATION_FIELDS.contains(entry.getKey())) {
                continue;
            }
            body.put(entry.getKey(), entry.getValue());
        }
        applyEditorMedia(body, slide, runtimeKind);
        body.remove("answerKey");
        QuestionKindCompiler.stripAllAnswers(body);
        applyQuestionSettings(body, slide, bankQuestionId, questionSettings, questionSetSettings);
        return body;
    }

    @SuppressWarnings("unchecked")
    private void applyEditorMedia(Map<String, Object> body, Map<String, Object> slide, String runtimeKind) {
        Object value = slide.get("media");
        if (!(value instanceof Map<?, ?> rawMedia)) return;
        Map<String, Object> media = (Map<String, Object>) rawMedia;
        String source = string(media.get("src"));
        if (source == null || source.isBlank()) return;

        if ("hotspot".equals(runtimeKind)) {
            Map<String, Object> image = new LinkedHashMap<>(asMap(body.get("hotspotImage")));
            image.put("url", source);
            image.putIfAbsent("width", 1200);
            image.putIfAbsent("height", 675);
            body.put("hotspotImage", image);
            return;
        }

        Map<String, Object> image = new LinkedHashMap<>();
        image.put("url", source);
        String alt = string(media.get("alt"));
        if (alt != null && !alt.isBlank()) image.put("alt", alt);
        body.put("contentImage", image);
    }

    private void applyQuestionSettings(
            Map<String, Object> body,
            Map<String, Object> slide,
            String bankQuestionId,
            Map<String, Map<String, Object>> questionSettings,
            Map<String, Map<String, Object>> questionSetSettings
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        mergeInto(merged, settingsFor(questionSettings, bankQuestionId));
        mergeInto(merged, asMap(slide.get("questionSettings")));
        mergeInto(merged, asMap(body.get("settings")));
        mergeInto(merged, settingsFor(questionSetSettings, bankQuestionId));
        mergeInto(merged, asMap(firstNonNull(
                slide.get("questionSetSettings"),
                slide.get("settingsOverride"),
                slide.get("overrideSettings")
        )));
        if (!merged.isEmpty()) {
            body.put("settings", merged);
        }
        body.remove("questionSettings");
        body.remove("questionSetSettings");
        body.remove("settingsOverride");
        body.remove("overrideSettings");
    }

    private Map<String, Object> settingsFor(Map<String, Map<String, Object>> settings, String questionId) {
        if (settings == null || questionId == null || questionId.isBlank()) {
            return null;
        }
        return settings.get(questionId);
    }

    private void mergeInto(Map<String, Object> target, Map<String, Object> source) {
        if (source != null) {
            target.putAll(source);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
