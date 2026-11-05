package com.erg.ergspringboot.lcms.quiz.publish;

import com.erg.ergspringboot.lcms.quiz.publish.CompiledQuiz.CompiledQuestion;
import com.erg.ergspringboot.lcms.quiz.publish.CompiledQuiz.CompiledSection;
import com.erg.ergspringboot.lcms.quiz.publish.QuestionKindMapping.Mapping;
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
 * <p>This class orchestrates: body building lives in {@link QuestionBodyCompiler}, per-kind answer
 * stripping and answer-key extraction in {@link QuestionKindStrategy}; it owns traversal, point
 * allocation and runtime-quiz assembly.
 *
 * <p>The compiler is deterministic and side-effect free so it can be unit tested per question kind and so
 * the resulting content hash is stable.
 */
@Service
public class QuizCompileService {

    private static final long PACKAGE_TOTAL_POINT_CENTS = 100_000L;

    private final QuestionBodyCompiler bodyCompiler = new QuestionBodyCompiler();

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

                Map<String, Object> runtimeSlide = bodyCompiler.mergeRuntimeQuestion(slide);
                Map<String, Object> body = bodyCompiler.compileBody(
                        runtimeSlide, mapping.runtimeKind(), bankQuestionId, questionSettings, questionSetSettings);
                Map<String, Object> key = QuestionKindStrategy.answerKeyFor(runtimeSlide, mapping.runtimeKind());
                PlayerBodyShuffler.apply(mapping.runtimeKind(), body, key);
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

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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
