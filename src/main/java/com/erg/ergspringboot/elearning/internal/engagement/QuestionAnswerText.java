package com.erg.ergspringboot.elearning.internal.engagement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Readable "correct answer" of a published question, from its answer key; null when the kind has no short text form. */
final class QuestionAnswerText {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private QuestionAnswerText() {
    }

    static String of(String bodyJson, String answerKeyJson) {
        Map<String, Object> key = parse(answerKeyJson);
        Map<String, Object> body = parse(bodyJson);
        if (key.get("correctChoiceId") != null) return choiceLabel(body, key.get("correctChoiceId").toString());
        if (key.get("correctChoiceIds") instanceof List<?> ids && !ids.isEmpty()) {
            return String.join("; ", ids.stream().map(id -> choiceLabel(body, String.valueOf(id))).toList());
        }
        if (key.get("correctAnswers") instanceof List<?> answers && !answers.isEmpty()) {
            return String.join("; ", answers.stream().map(String::valueOf).toList());
        }
        if (key.get("value") != null) return String.valueOf(key.get("value"));
        return null;
    }

    private static String choiceLabel(Map<String, Object> body, String id) {
        List<Object> choices = new ArrayList<>();
        for (String field : List.of("choices", "options")) {
            if (body.get(field) instanceof List<?> list) choices.addAll(list);
        }
        for (Object choice : choices) {
            if (choice instanceof Map<?, ?> map && id.equals(String.valueOf(map.get("id")))) {
                for (String field : List.of("label", "text", "title")) {
                    if (map.get(field) != null && !map.get(field).toString().isBlank()) return map.get(field).toString();
                }
            }
        }
        return id;
    }

    private static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = JSON.readValue(json, MAP);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception malformed) {
            return Map.of();
        }
    }
}
