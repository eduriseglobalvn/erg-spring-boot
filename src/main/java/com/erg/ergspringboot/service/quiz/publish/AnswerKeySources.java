package com.erg.ergspringboot.service.quiz.publish;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the correctness data out of an authoring slide, in the shapes {@link QuestionKindCompiler} keys on. */
final class AnswerKeySources {

    private AnswerKeySources() {
    }

    static String correctOptionId(Map<String, Object> slide) {
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

    static List<String> correctOptionIds(Map<String, Object> slide) {
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
    static List<String> ids(Object value) {
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
    static List<Map<String, Object>> matchingPairs(Object value) {
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
    static List<Map<String, Object>> blankAnswers(Object value) {
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

    static List<Map<String, Object>> textBlankAnswers(Object value) {
        return idAnswers(value, "correctAnswers", false);
    }

    static List<Map<String, Object>> inlineBlankAnswers(Object value) {
        return idAnswers(value, "correctOptionId", true);
    }

    static List<Map<String, Object>> wordSlotAnswers(Object value) {
        return idAnswers(value, "correctWordId", true);
    }

    /** {@code [{id, answers}]} for items carrying both an id and the answer field; single answers are listed. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> idAnswers(Object value, String answerField, boolean singleAnswer) {
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
    static List<Map<String, Object>> correctHotspotAreas(Object value) {
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
    static List<Map<String, Object>> dragDropTargets(Object value) {
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

    static void putIfPresent(Map<String, Object> key, String field, Object value) {
        if (value != null) {
            key.put(field, value);
        }
    }

    @SuppressWarnings("unchecked")
    static Object mapValue(Object holder, String field) {
        if (holder instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).get(field);
        }
        return null;
    }

    static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static boolean boolValue(Object value) {
        return value instanceof Boolean bool && bool;
    }}
