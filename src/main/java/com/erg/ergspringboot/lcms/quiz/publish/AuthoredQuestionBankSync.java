package com.erg.ergspringboot.lcms.quiz.publish;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.ContentScope;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Question;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionChoice;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.Quiz;
import com.erg.ergspringboot.lcms.quiz.publish.CompiledQuiz.CompiledQuestion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors questions authored inline in the quiz editor into the question bank, so "Xuất bản" also makes them
 * show up under "Ngân hàng câu hỏi". Re-publishing updates the same bank row (the link is the previous version's
 * {@code bank_question_id} for the same source slide); a bank row someone archived is never resurrected.
 *
 * <p>shortcut: only single choice, multiple response, true/false and short answer are mirrored; matching, sequence,
 * fill-in and the rest keep living in the quiz alone until their bank {@code content} shape is mapped.
 */
final class AuthoredQuestionBankSync {

    private static final Set<String> MIRRORED_KINDS = Set.of("single_choice", "multiple_response", "true_false", "short_answer");
    private static final int SCHEMA_VERSION = 1;

    private final QuestionRepository questions;

    AuthoredQuestionBankSync(QuestionRepository questions) {
        this.questions = questions;
    }

    /** The bank question this compiled question maps to, or {@code null} when it is not mirrored. */
    String upsert(String tenantId, String userId, Quiz quiz, CompiledQuestion compiled, String previousBankQuestionId) {
        if (!MIRRORED_KINDS.contains(compiled.kind())) return null;
        if (blank(quiz.getSubjectId()) || blank(quiz.getLevelId()) || blank(quiz.getCategoryId())) return null;
        String stem = text(compiled.body() == null ? null : compiled.body().get("title"));
        if (stem == null) return null;

        Question question = blank(previousBankQuestionId) ? null
                : questions.findByIdAndTenantId(previousBankQuestionId, tenantId).orElse(null);
        if (question != null && question.getArchivedAt() != null) return question.getId();
        if (question == null) {
            question = new Question();
            question.setTenantId(tenantId);
            question.setOwnerUserId(blank(userId) ? null : userId);
            question.setSchemaVersion(SCHEMA_VERSION);
            question.setMasteryRate(0.0d);
            question.setUsageCount(0);
            question.setSchoolsUsing(0);
            question.setStatus("ready");
            question.setDifficulty("core");
        }
        question.setScope(quiz.getScope() == null ? new ContentScope("global", null) : quiz.getScope());
        question.setSubjectId(quiz.getSubjectId());
        question.setLevelId(quiz.getLevelId());
        question.setTopicId(quiz.getCategoryId());
        question.setCategoryId(quiz.getCategoryId());
        question.setType(compiled.kind());
        question.setStem(stem);
        fillAnswer(question, compiled);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "quiz-editor");
        metadata.put("quizId", quiz.getId());
        metadata.put("slideId", compiled.sourceSlideId());
        question.setMetadata(metadata);
        question.setSettings(Map.of());
        // Flush now: quiz_version_questions.bank_question_id is a real FK and Hibernate may order that insert first.
        Question saved = questions.saveAndFlush(question);
        return (saved == null ? question : saved).getId();
    }

    private void fillAnswer(Question question, CompiledQuestion compiled) {
        Map<String, Object> key = compiled.answerKey() == null ? Map.of() : compiled.answerKey();
        if (compiled.kind().equals("short_answer")) {
            List<String> accepted = acceptedAnswers(key);
            question.setChoices(List.of());
            question.setAnswer(accepted.isEmpty() ? null : accepted.get(0));
            question.setContent(Map.of("acceptedAnswers", accepted));
            return;
        }
        Set<String> correctIds = new java.util.LinkedHashSet<>();
        if (key.get("correctChoiceId") instanceof String id) correctIds.add(id);
        if (key.get("correctChoiceIds") instanceof List<?> ids) ids.forEach(id -> correctIds.add(String.valueOf(id)));
        List<QuestionChoice> choices = new ArrayList<>();
        List<String> correctLabels = new ArrayList<>();
        if (compiled.body() != null && compiled.body().get("choices") instanceof List<?> raw) {
            for (Object item : raw) {
                if (!(item instanceof Map<?, ?> choice)) continue;
                String id = text(choice.get("id"));
                String label = text(choice.get("label"));
                if (id == null || label == null) continue;
                boolean correct = correctIds.contains(id);
                choices.add(new QuestionChoice(id, label, correct));
                if (correct) correctLabels.add(label);
            }
        }
        question.setChoices(choices);
        question.setAnswer(compiled.kind().equals("multiple_response") ? correctLabels : correctLabels.isEmpty() ? null : correctLabels.get(0));
        question.setContent(Map.of());
    }

    private List<String> acceptedAnswers(Map<String, Object> key) {
        List<String> accepted = new ArrayList<>();
        if (key.get("blanks") instanceof List<?> blanks) {
            for (Object blank : blanks) {
                if (blank instanceof Map<?, ?> map && map.get("answers") instanceof List<?> answers) {
                    answers.forEach(answer -> {
                        String value = text(answer);
                        if (value != null && !accepted.contains(value)) accepted.add(value);
                    });
                }
            }
        }
        return accepted;
    }

    private static String text(Object value) {
        if (value == null) return null;
        String string = value.toString().trim();
        return string.isEmpty() ? null : string;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
