package com.erg.ergspringboot.lcms.quiz.publish;

import com.erg.ergspringboot.lcms.persistence.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizQuestionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Compiles a draft together with the bank-question and question-set settings it references. */
final class QuizDraftCompiler {

    private final QuestionRepository bankQuestionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizCompileService compileService;

    QuizDraftCompiler(
            QuestionRepository bankQuestionRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizCompileService compileService
    ) {
        this.bankQuestionRepository = bankQuestionRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.compileService = compileService;
    }

    CompiledQuiz compileWithSettings(String tenantId, String quizId, QuizDraft draft) {
        List<String> bankQuestionIds = extractBankQuestionIds(draft.getTree());
        Map<String, Map<String, Object>> questionSettings = new LinkedHashMap<>();
        if (!bankQuestionIds.isEmpty()) {
            bankQuestionRepository.findByTenantIdAndIdIn(tenantId, bankQuestionIds).forEach(question -> {
                if (question.getSettings() != null && !question.getSettings().isEmpty()) {
                    questionSettings.put(question.getId(), question.getSettings());
                }
            });
        }

        Map<String, Map<String, Object>> questionSetSettings = new LinkedHashMap<>();
        quizQuestionRepository.findByTenantIdAndQuizIdOrderByOrderIndexAsc(tenantId, quizId).forEach(link -> {
            if (link.getSettings() != null && !link.getSettings().isEmpty()) {
                questionSetSettings.put(link.getQuestionId(), link.getSettings());
            }
        });
        return compileService.compile(draft.getSettings(), draft.getTree(), questionSettings, questionSetSettings, draft.getPlayerTemplate());
    }

    private List<String> extractBankQuestionIds(Object tree) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> group : asMapList(tree)) {
            Object slides = group.get("slides");
            for (Map<String, Object> slide : asMapList(slides)) {
                Object bankQuestionId = firstNonNull(slide.get("bankQuestionId"), slide.get("questionId"));
                if (bankQuestionId instanceof String value && !value.isBlank()) {
                    ids.add(value.trim());
                }
            }
        }
        return List.copyOf(ids);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
