package com.erg.ergspringboot.lcms.quiz.publish;

import com.erg.ergspringboot.lcms.quiz.publish.CompiledQuiz.CompiledQuestion;
import com.erg.ergspringboot.lcms.quiz.publish.CompiledQuiz.CompiledSection;
import com.erg.ergspringboot.lcms.quiz.publish.QuestionKindMapping.Mapping;
import com.erg.ergspringboot.utils.IdGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
        return compile(settings, tree, questionSettings, questionSetSettings, null);
    }

    /** {@code playerTemplate} is the teacher's chosen player look (draft.playerTemplate); absent or empty adds no key. */
    @SuppressWarnings("unchecked")
    public CompiledQuiz compile(
            Map<String, Object> settings,
            Object tree,
            Map<String, Map<String, Object>> questionSettings,
            Map<String, Map<String, Object>> questionSetSettings,
            Map<String, Object> playerTemplate
    ) {
        List<CompiledSection> sections = new ArrayList<>();
        List<CompiledQuestion> questions = new ArrayList<>();
        Map<String, Object> answerKey = new LinkedHashMap<>();

        List<Map<String, Object>> groups = asMapList(tree);
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
                        explicitPoints(body),
                        mapping.autoGradable(),
                        mapping.gradingPolicy(),
                        questionOrder++,
                        body,
                        key,
                        slide.get("runtimeQuestion") instanceof Map<?, ?> authored ? (Map<String, Object>) authored : null
                ));
            }
        }

        questions = allocatePoints(questions);
        Map<String, Object> runtimeQuiz = buildRuntimeQuiz(settings, playerTemplate, sections, questions);
        return new CompiledQuiz(sections, questions, runtimeQuiz, answerKey);
    }

    private Map<String, Object> buildRuntimeQuiz(
            Map<String, Object> settings,
            Map<String, Object> playerTemplate,
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
        if (playerTemplate != null && !playerTemplate.isEmpty()) {
            runtime.put("playerTemplate", playerTemplate);
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

    /**
     * Questions keep their own positive points (settings.points, e.g. the editor's questionSetSettings.points).
     * The rest split what is left of the 1000-point package equally (floored at 0); with no own points at all
     * this is the original equal 1000 split, so existing quizzes compile unchanged.
     */
    private List<CompiledQuestion> allocatePoints(List<CompiledQuestion> questions) {
        long ownCents = 0;
        int unsetCount = 0;
        for (CompiledQuestion question : questions) {
            if (question.points() == null) {
                unsetCount += 1;
            } else {
                ownCents += question.points().movePointRight(2).longValue();
            }
        }
        long remainingCents = Math.max(0, PACKAGE_TOTAL_POINT_CENTS - ownCents);
        List<CompiledQuestion> result = new ArrayList<>(questions.size());
        int unsetIndex = 0;
        for (CompiledQuestion q : questions) {
            BigDecimal points = q.points() != null ? q.points() : splitPoints(remainingCents, unsetIndex++, unsetCount);
            result.add(new CompiledQuestion(q.runtimeQuestionId(), q.sourceSlideId(), q.sectionId(), q.bankQuestionId(),
                    q.kind(), q.authoringType(), points, q.autoGradable(), q.gradingPolicy(), q.orderIndex(),
                    q.body(), q.answerKey(), q.authoredQuestion()));
        }
        return result;
    }

    private BigDecimal splitPoints(long totalCents, int index, int count) {
        long cents = totalCents / count + (index < totalCents % count ? 1 : 0);
        return BigDecimal.valueOf(cents, 2);
    }

    /** Positive settings.points rounded to cents, or null when absent/invalid (falls back to the equal split). */
    private BigDecimal explicitPoints(Map<String, Object> body) {
        if (!(body.get("settings") instanceof Map<?, ?> settings)) {
            return null;
        }
        Object value = settings.get("points");
        if (value == null) {
            return null;
        }
        try {
            BigDecimal points = new BigDecimal(value.toString().trim()).setScale(2, RoundingMode.HALF_UP);
            return points.signum() > 0 ? points : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
