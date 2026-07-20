package com.erg.ergspringboot.service.quiz.publish;

import com.erg.ergspringboot.service.quiz.publish.CompiledQuiz.CompiledQuestion;
import com.erg.ergspringboot.service.quiz.publish.CompiledQuiz.CompiledSection;
import com.erg.ergspringboot.service.quiz.publish.QuestionKindMapping.Mapping;
import com.erg.ergspringboot.utils.IdGenerator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Compiles an authoring draft tree (groups -> slides) into a normalized runtime quiz (spec §3).
 *
 * <p>Each non-presentation slide becomes a runtime question whose authoring type is mapped to a runtime
 * kind (via {@link QuestionKindMapping}). Correctness is split out: the player-facing {@code body} is
 * stripped of correctness markers (choice {@code correct} flags, blank answers, hotspot regions), while
 * the {@code answerKey} holds them server-side. Presentation-only fields (textStyles, layout, animation)
 * are dropped from the runtime body. Groups become sections.
 *
 * <p>The compiler is deterministic and side-effect free so it can be unit tested per question kind and so
 * the resulting content hash is stable.
 */
@Service
public class QuizCompileService {

    private static final long PACKAGE_TOTAL_POINT_CENTS = 100_000L;

    // Presentation-only slide fields that never affect grading and are dropped from runtime body (§3).
    private static final java.util.Set<String> PRESENTATION_FIELDS = java.util.Set.of(
            "textStyles", "elementOffsets", "layoutPreset", "animationPreset", "themeColor",
            "layers", "infoPage", "media"
    );

    @SuppressWarnings("unchecked")
    public CompiledQuiz compile(Map<String, Object> settings, Object tree) {
        return compile(settings, tree, Map.of(), Map.of());
    }

    @SuppressWarnings("unchecked")
    public CompiledQuiz compile(
            Map<String, Object> settings,
            Object tree,
            Map<String, Map<String, Object>> questionSettings,
            Map<String, Map<String, Object>> questionSetSettings
    ) {
        List<CompiledSection> sections = new ArrayList<>();
        List<CompiledQuestion> questions = new ArrayList<>();
        Map<String, Object> answerKey = new LinkedHashMap<>();

        List<Map<String, Object>> groups = asMapList(tree);
        int totalQuestionCount = countQuestionSlides(groups);
        int sectionOrder = 0;
        int questionOrder = 0;
        for (Map<String, Object> group : groups) {
            String sectionId = IdGenerator.newId();
            String title = string(group.get("title"));
            sections.add(new CompiledSection(sectionId, title, sectionOrder++));

            List<Map<String, Object>> slides = asMapList(group.get("slides"));
            for (Map<String, Object> slide : slides) {
                String authoringType = firstNonBlank(string(slide.get("type")), string(slide.get("kind")));
                if (!QuestionKindMapping.isQuestionType(authoringType)) {
                    continue;
                }
                Mapping mapping = QuestionKindMapping.forAuthoringType(authoringType);
                String runtimeQuestionId = firstNonBlank(
                        string(slide.get("questionId")), string(slide.get("slideId")), string(slide.get("id")), IdGenerator.newId());
                String sourceSlideId = firstNonBlank(string(slide.get("slideId")), string(slide.get("id")));
                String bankQuestionId = firstNonBlank(string(slide.get("bankQuestionId")), string(slide.get("questionId")));
                BigDecimal points = autoQuestionPoints(questionOrder, totalQuestionCount);

                Map<String, Object> body = compileBody(slide, mapping.runtimeKind());
                applyQuestionSettings(body, slide, bankQuestionId, questionSettings, questionSetSettings);
                Map<String, Object> key = extractAnswerKey(slide, mapping.runtimeKind());
                if (!key.isEmpty()) {
                    answerKey.put(runtimeQuestionId, key);
                }

                questions.add(new CompiledQuestion(
                        runtimeQuestionId,
                        sourceSlideId,
                        sectionId,
                        bankQuestionId,
                        mapping.runtimeKind(),
                        authoringType,
                        points,
                        mapping.autoGradable(),
                        mapping.gradingPolicy(),
                        questionOrder++,
                        body,
                        key
                ));
            }
        }

        Map<String, Object> runtimeQuiz = buildRuntimeQuiz(settings, sections, questions);
        return new CompiledQuiz(sections, questions, runtimeQuiz, answerKey);
    }

    private Map<String, Object> buildRuntimeQuiz(
            Map<String, Object> settings,
            List<CompiledSection> sections,
            List<CompiledQuestion> questions
    ) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("schemaVersion", 1);
        if (settings != null) {
            Object info = settings.get("info");
            if (info != null) {
                runtime.put("info", info);
            }
            Object runtimeSettings = firstNonNull(settings.get("runtime"), settings.get("settings"));
            if (runtimeSettings != null) {
                runtime.put("settings", runtimeSettings);
            }
            Object result = settings.get("result");
            if (result != null) {
                runtime.put("result", result);
            }
            Object theme = settings.get("theme");
            if (theme != null) {
                runtime.put("theme", theme);
            }
        }
        List<Map<String, Object>> runtimeSections = new ArrayList<>();
        for (CompiledSection section : sections) {
            Map<String, Object> sectionMap = new LinkedHashMap<>();
            sectionMap.put("id", section.sectionId());
            sectionMap.put("title", section.title());
            List<Map<String, Object>> sectionQuestions = new ArrayList<>();
            for (CompiledQuestion question : questions) {
                if (section.sectionId().equals(question.sectionId())) {
                    Map<String, Object> body = new LinkedHashMap<>(question.body());
                    body.put("id", question.runtimeQuestionId());
                    body.put("kind", question.kind());
                    body.put("points", question.points());
                    sectionQuestions.add(body);
                }
            }
            sectionMap.put("questions", sectionQuestions);
            runtimeSections.add(sectionMap);
        }
        runtime.put("sections", runtimeSections);
        return runtime;
    }

    /** Copies the slide into a runtime body, dropping presentation fields and correctness markers. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> compileBody(Map<String, Object> slide, String runtimeKind) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : slide.entrySet()) {
            if (PRESENTATION_FIELDS.contains(entry.getKey())) {
                continue;
            }
            body.put(entry.getKey(), entry.getValue());
        }
        // Strip correctness markers so the player-facing body never leaks answers (§6).
        stripCorrectnessFromList(body, "options");
        stripCorrectnessFromList(body, "choices");
        body.remove("correctChoiceId");
        body.remove("correctChoiceIds");
        body.remove("correctAnswers");
        body.remove("correctAnswer");
        body.remove("answerKey");
        body.remove("tolerance");
        stripBlankAnswers(body, "blanks");
        stripTextBlankAnswers(body, "textBlanks");
        stripNumericAnswer(body);
        stripInlineAnswers(body);
        stripDragAnswers(body);
        stripHotspotAnswers(body);
        return body;
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private void stripCorrectnessFromList(Map<String, Object> body, String field) {
        Object options = body.get(field);
        if (options instanceof List<?> list) {
            List<Map<String, Object>> stripped = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> option = new LinkedHashMap<>((Map<String, Object>) map);
                    option.remove("correct");
                    option.remove("isCorrect");
                    stripped.add(option);
                }
            }
            body.put(field, stripped);
        }
    }

    @SuppressWarnings("unchecked")
    private void stripBlankAnswers(Map<String, Object> body, String field) {
        Object blanks = body.get(field);
        if (blanks instanceof List<?> blankList) {
            List<Map<String, Object>> stripped = new ArrayList<>();
            for (Object item : blankList) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> blank = new LinkedHashMap<>((Map<String, Object>) map);
                    blank.remove("answers");
                    blank.remove("answer");
                    blank.remove("correct");
                    stripped.add(blank);
                }
            }
            body.put(field, stripped);
        }
    }

    @SuppressWarnings("unchecked")
    private void stripTextBlankAnswers(Map<String, Object> body, String field) {
        Object blanks = body.get(field);
        if (blanks instanceof List<?> blankList) {
            List<Map<String, Object>> stripped = new ArrayList<>();
            for (Object item : blankList) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> blank = new LinkedHashMap<>((Map<String, Object>) map);
                    blank.remove("correctAnswers");
                    stripped.add(blank);
                }
            }
            body.put(field, stripped);
        }
    }

    @SuppressWarnings("unchecked")
    private void stripNumericAnswer(Map<String, Object> body) {
        Object numericAnswer = body.get("numericAnswer");
        if (numericAnswer instanceof Map<?, ?> map) {
            Map<String, Object> stripped = new LinkedHashMap<>((Map<String, Object>) map);
            stripped.remove("correctValue");
            stripped.remove("tolerance");
            body.put("numericAnswer", stripped);
        }
    }

    @SuppressWarnings("unchecked")
    private void stripInlineAnswers(Map<String, Object> body) {
        Object inlineBlanks = body.get("inlineBlanks");
        if (inlineBlanks instanceof List<?> list) {
            List<Map<String, Object>> stripped = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> blank = new LinkedHashMap<>((Map<String, Object>) map);
                    blank.remove("correctOptionId");
                    stripped.add(blank);
                }
            }
            body.put("inlineBlanks", stripped);
        }
    }

    @SuppressWarnings("unchecked")
    private void stripDragAnswers(Map<String, Object> body) {
        Object wordSlots = body.get("wordSlots");
        if (wordSlots instanceof List<?> list) {
            List<Map<String, Object>> stripped = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> slot = new LinkedHashMap<>((Map<String, Object>) map);
                    slot.remove("correctWordId");
                    stripped.add(slot);
                }
            }
            body.put("wordSlots", stripped);
        }
        Object dragDropItems = body.get("dragDropItems");
        if (dragDropItems instanceof List<?> list) {
            List<Map<String, Object>> stripped = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> dragItem = new LinkedHashMap<>((Map<String, Object>) map);
                    dragItem.remove("correctTargetId");
                    stripped.add(dragItem);
                }
            }
            body.put("dragDropItems", stripped);
        }
    }

    @SuppressWarnings("unchecked")
    private void stripHotspotAnswers(Map<String, Object> body) {
        Object areas = body.get("hotspotAreas");
        if (areas instanceof List<?> list) {
            List<Map<String, Object>> stripped = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> area = new LinkedHashMap<>((Map<String, Object>) map);
                    area.remove("correct");
                    stripped.add(area);
                }
            }
            body.put("hotspotAreas", stripped);
        }
    }

    /** Pulls the correctness data for a slide into the server-only answer key (§3 answer-key split). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAnswerKey(Map<String, Object> slide, String runtimeKind) {
        Map<String, Object> key = new LinkedHashMap<>();
        switch (runtimeKind) {
            case "single_choice", "true_false" -> {
                String correct = correctOptionId(slide);
                if (correct != null) {
                    key.put("correctChoiceId", correct);
                }
            }
            case "multiple_response" -> {
                List<String> correct = correctOptionIds(slide);
                if (!correct.isEmpty()) {
                    key.put("correctChoiceIds", correct);
                    key.put("acceptPartial", boolValue(slide.get("acceptPartial")));
                }
            }
            case "short_answer" -> {
                putIfPresent(key, "correctAnswers", slide.get("correctAnswers"));
                putIfPresent(key, "blanks", textBlankAnswers(slide.get("textBlanks")));
            }
            case "numeric" -> {
                Object numericAnswer = slide.get("numericAnswer");
                putIfPresent(key, "value", firstNonNull(slide.get("correctAnswer"), slide.get("value"), mapValue(numericAnswer, "correctValue")));
                putIfPresent(key, "tolerance", firstNonNull(slide.get("tolerance"), mapValue(numericAnswer, "tolerance")));
            }
            case "sequence" -> putIfPresent(key, "order", firstNonNull(slide.get("correctOrder"), slide.get("order"), ids(slide.get("sequenceItems"))));
            case "matching" -> putIfPresent(key, "pairs", firstNonNull(slide.get("correctPairs"), slide.get("pairs"), matchingPairs(slide.get("matching"))));
            case "fill_blank", "drag_words", "select_from_lists" -> {
                Object answers = firstNonNull(blankAnswers(slide.get("blanks")), textBlankAnswers(slide.get("textBlanks")),
                        inlineBlankAnswers(slide.get("inlineBlanks")), wordSlotAnswers(slide.get("wordSlots")));
                putIfPresent(key, "blanks", answers);
            }
            case "hotspot" -> putIfPresent(key, "regions", firstNonNull(slide.get("correctRegions"), slide.get("regions"), correctHotspotAreas(slide.get("hotspotAreas"))));
            case "drag_drop" -> putIfPresent(key, "targets", firstNonNull(slide.get("correctTargets"), slide.get("targets"), dragDropTargets(slide.get("dragDropItems"))));
            default -> {
                // essay/likert have no answer key.
            }
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private String correctOptionId(Map<String, Object> slide) {
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

    @SuppressWarnings("unchecked")
    private List<String> correctOptionIds(Map<String, Object> slide) {
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

    private void putIfPresent(Map<String, Object> key, String field, Object value) {
        if (value != null) {
            key.put(field, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Object mapValue(Object holder, String field) {
        if (holder instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).get(field);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> ids(Object value) {
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
    private List<Map<String, Object>> matchingPairs(Object value) {
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
    private List<Map<String, Object>> blankAnswers(Object value) {
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> textBlankAnswers(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> blank = (Map<String, Object>) map;
                    if (blank.get("id") != null && blank.get("correctAnswers") != null) {
                        result.add(Map.of("id", blank.get("id"), "answers", blank.get("correctAnswers")));
                    }
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> inlineBlankAnswers(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> blank = (Map<String, Object>) map;
                    if (blank.get("id") != null && blank.get("correctOptionId") != null) {
                        result.add(Map.of("id", blank.get("id"), "answers", List.of(blank.get("correctOptionId"))));
                    }
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> wordSlotAnswers(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> slot = (Map<String, Object>) map;
                    if (slot.get("id") != null && slot.get("correctWordId") != null) {
                        result.add(Map.of("id", slot.get("id"), "answers", List.of(slot.get("correctWordId"))));
                    }
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> correctHotspotAreas(Object value) {
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
    private List<Map<String, Object>> dragDropTargets(Object value) {
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

    private int countQuestionSlides(List<Map<String, Object>> groups) {
        int count = 0;
        for (Map<String, Object> group : groups) {
            for (Map<String, Object> slide : asMapList(group.get("slides"))) {
                String authoringType = firstNonBlank(string(slide.get("type")), string(slide.get("kind")));
                if (QuestionKindMapping.isQuestionType(authoringType)) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private BigDecimal autoQuestionPoints(int questionIndex, int totalQuestionCount) {
        if (totalQuestionCount <= 0) {
            return BigDecimal.ZERO;
        }
        long baseCents = PACKAGE_TOTAL_POINT_CENTS / totalQuestionCount;
        long remainderCents = PACKAGE_TOTAL_POINT_CENTS % totalQuestionCount;
        long cents = baseCents + (questionIndex < remainderCents ? 1 : 0);
        return BigDecimal.valueOf(cents, 2);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
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

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
