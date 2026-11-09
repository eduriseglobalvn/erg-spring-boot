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
 * Every question kind is mirrored: choice kinds also fill {@code choices}/{@code answer}, and every row keeps the
 * editor's runtimeQuestion in {@code content.runtimeQuestion} so a package built from the bank gets the whole question.
 */
final class AuthoredQuestionBankSync {

    private static final Set<String> CHOICE_KINDS = Set.of("single_choice", "multiple_response", "true_false");
    private static final int SCHEMA_VERSION = 1;

    private final QuestionRepository questions;

    AuthoredQuestionBankSync(QuestionRepository questions) {
        this.questions = questions;
    }

    /** The bank question this compiled question maps to, or {@code null} when it is not mirrored. */
    String upsert(String tenantId, String userId, Quiz quiz, CompiledQuestion compiled, String previousBankQuestionId) {
        if (blank(quiz.getSubjectId()) || blank(quiz.getLevelId()) || blank(quiz.getCategoryId())) return null;
        String stem = text(compiled.body() == null ? null : compiled.body().get("title"));
        if (stem == null && compiled.authoredQuestion() != null) stem = text(compiled.authoredQuestion().get("title"));
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

    /** Retires bank rows the previous version mirrored from slides this version no longer has. */
    void retireUnused(String tenantId, java.util.Collection<String> previous, java.util.Collection<String> kept) {
        for (String id : previous) {
            if (blank(id) || kept.contains(id)) continue;
            questions.findByIdAndTenantId(id, tenantId)
                    .filter(row -> row.getArchivedAt() == null && row.getMetadata() != null
                            && "quiz-editor".equals(row.getMetadata().get("source")))
                    .ifPresent(row -> {
                        row.setArchivedAt(java.time.OffsetDateTime.now());
                        questions.save(row);
                    });
        }
    }

    private void fillAnswer(Question question, CompiledQuestion compiled) {
        Map<String, Object> key = compiled.answerKey() == null ? Map.of() : compiled.answerKey();
        Map<String, Object> content = new LinkedHashMap<>();
        if (compiled.authoredQuestion() != null) content.put("runtimeQuestion", compiled.authoredQuestion());
        question.setContent(content);
        if (!CHOICE_KINDS.contains(compiled.kind())) {
            List<String> accepted = compiled.kind().equals("short_answer") ? acceptedAnswers(key) : List.of();
            question.setChoices(List.of());
            question.setAnswer(accepted.isEmpty() ? null : accepted.get(0));
            if (!accepted.isEmpty()) content.put("acceptedAnswers", accepted);
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
