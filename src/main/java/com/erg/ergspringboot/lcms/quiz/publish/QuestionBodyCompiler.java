package com.erg.ergspringboot.lcms.quiz.publish;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the player-facing body of one runtime question from its authoring slide (spec §3): flattens the
 * editor's {@code runtimeQuestion}, drops presentation-only fields, maps editor media, strips correctness
 * markers through {@link QuestionKindStrategy} and merges the layered question settings.
 *
 * <p>Deterministic and side-effect free; the input slide is never mutated.
 */
final class QuestionBodyCompiler {

    // Presentation-only slide fields that never affect grading and are dropped from runtime body (§3).
    private static final Set<String> PRESENTATION_FIELDS = Set.of(
            "textStyles", "elementOffsets", "layoutPreset", "animationPreset", "themeColor",
            "layers", "infoPage", "media", "runtimeQuestion",
            // Editor-only: the learner never reads them (the editor option set is an object, runtime options an array).
            "status", "textStyle", "feedbackRows", "choiceControlType"
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
        // Runtime drag items carry correctTargetId (the dropTargets ids learners submit); the editor's only the group label.
        if (runtimeQuestion instanceof Map<?, ?> map && map.get("dragDropItems") instanceof List<?> items) {
            merged.put("dragDropItems", withSlideMedia(items, slide.get("dragDropItems")));
        }
        foldKeyedMatching(merged);
        return merged;
    }

    /**
     * A matching question in the learner shape (prompts, separate responses, matchingKey) is folded back into
     * pairs that carry their own response, the shape the key extraction and the shuffler read. A pair the key
     * does not reach keeps its own response fields.
     */
    @SuppressWarnings("unchecked")
    private static void foldKeyedMatching(Map<String, Object> merged) {
        if (!(merged.get("matchingKey") instanceof Map<?, ?> key)
                || !(merged.get("matchingResponses") instanceof List<?> responses)
                || !(merged.get("matching") instanceof List<?> pairs)) {
            return;
        }
        Map<Object, Map<String, Object>> responseById = new LinkedHashMap<>();
        for (Object response : responses) {
            if (response instanceof Map<?, ?> map && map.get("id") != null) {
                responseById.put(map.get("id"), (Map<String, Object>) map);
            }
        }
        List<Object> folded = new ArrayList<>(pairs.size());
        for (Object pair : pairs) {
            if (!(pair instanceof Map<?, ?> map)) {
                folded.add(pair);
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
            Map<String, Object> response = responseById.get(key.get(map.get("id")));
            if (response != null) {
                response.forEach((field, value) -> {
                    if (field.startsWith("response")) {
                        copy.put(field, value);
                    }
                });
            }
            folded.add(copy);
        }
        merged.put("matching", folded);
        merged.remove("matchingResponses");
        merged.remove("matchingKey");
    }

    /** Runtime items keep the slide item's {@code media} when they have no image of their own (older runtime questions). */
    @SuppressWarnings("unchecked")
    private List<Object> withSlideMedia(List<?> runtimeItems, Object slideItems) {
        Map<Object, Object> mediaById = new LinkedHashMap<>();
        if (slideItems instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("id") != null && map.get("media") != null) {
                    mediaById.put(map.get("id"), map.get("media"));
                }
            }
        }
        List<Object> result = new ArrayList<>(runtimeItems.size());
        for (Object item : runtimeItems) {
            if (item instanceof Map<?, ?> map && map.get("image") == null && mediaById.containsKey(map.get("id"))) {
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
                copy.put("media", mediaById.get(map.get("id")));
                item = copy;
            }
            result.add(item);
        }
        return result;
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
        applyFeedbackRows(body, slide);
        if (body.get("options") instanceof Map) {
            body.remove("options");
        }
        // The merge lets the slide's editor kind override the runtime one; the learner body carries the runtime kind.
        body.put("kind", runtimeKind);
        // The editor keeps choices for every kind (their labels can be the answers, in sequence their correct order)
        // and dragDropItems for matching (target = the pair); the learner reads them only for their own kinds.
        QuestionKindStrategy strategy = QuestionKindStrategy.forRuntimeKind(runtimeKind);
        if (strategy != QuestionKindStrategy.EXACT_CHOICE && strategy != QuestionKindStrategy.MULTIPLE_RESPONSE) {
            body.remove("choices");
        }
        if (strategy != QuestionKindStrategy.DRAG_DROP) {
            body.remove("dragDropItems");
        }
        applyEditorMedia(body, slide, runtimeKind);
        applyItemMedia(body, "choices");
        applyItemMedia(body, "dragDropItems");
        body.remove("answerKey");
        QuestionKindStrategy.stripAllAnswers(body);
        applyQuestionSettings(body, slide, bankQuestionId, questionSettings, questionSetSettings);
        return body;
    }

    /**
     * A slide made from a question-bank question keeps its "when right / wrong" messages only in the editor's
     * feedbackRows ({kind, feedback}); the learner reads {@code feedback: {correct, partial, incorrect}}. A feedback
     * object the editor already wrote on the runtime question wins.
     */
    private void applyFeedbackRows(Map<String, Object> body, Map<String, Object> slide) {
        if (body.get("feedback") instanceof Map || !(slide.get("feedbackRows") instanceof List<?> rows)) {
            return;
        }
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("correct", "");
        feedback.put("partial", "");
        feedback.put("incorrect", "");
        boolean any = false;
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map && feedback.containsKey(string(map.get("kind")))) {
                String text = string(map.get("feedback"));
                if (text != null && !text.isBlank()) {
                    feedback.put(string(map.get("kind")), text);
                    any = true;
                }
            }
        }
        if (any) {
            body.put("feedback", feedback);
        }
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

    /**
     * Learners read {@code image: {url, alt}} on choices and drag items; the editor stores {@code media: {src, alt}}.
     * Copies every item so the authoring slide is never mutated.
     */
    @SuppressWarnings("unchecked")
    private void applyItemMedia(Map<String, Object> body, String field) {
        if (!(body.get(field) instanceof List<?> items)) return;
        List<Object> mapped = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> rawItem)) {
                mapped.add(item);
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) rawItem);
            Object media = copy.remove("media");
            if (media instanceof Map<?, ?> rawMedia) {
                String source = string(rawMedia.get("src"));
                if (source != null && !source.isBlank()) {
                    Map<String, Object> image = new LinkedHashMap<>();
                    image.put("url", source);
                    String alt = string(rawMedia.get("alt"));
                    if (alt != null && !alt.isBlank()) image.put("alt", alt);
                    copy.put("image", image);
                }
            }
            mapped.add(copy);
        }
        body.put(field, mapped);
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
