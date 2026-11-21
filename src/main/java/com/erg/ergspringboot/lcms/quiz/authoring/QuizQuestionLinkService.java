package com.erg.ergspringboot.lcms.quiz.authoring;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizQuestion;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizQuestionRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class QuizQuestionLinkService {

    private final QuizQuestionRepository quizQuestionRepository;
    private final QuestionRepository questionRepository;
    private final JdbcTemplate jdbcTemplate;

    public QuizQuestionLinkService(
            QuizQuestionRepository quizQuestionRepository,
            QuestionRepository questionRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.quizQuestionRepository = quizQuestionRepository;
        this.questionRepository = questionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> syncManualQuestionIds(String tenantId, String quizId, List<String> questionIds) {
        List<QuestionLinkInput> inputs = questionIds == null
                ? List.of()
                : questionIds.stream().map(id -> new QuestionLinkInput(id, Map.of(), null)).toList();
        return syncManualQuestionLinks(tenantId, quizId, inputs);
    }

    public List<String> syncManualQuestionLinks(String tenantId, String quizId, List<QuestionLinkInput> questionLinks) {
        List<QuestionLinkInput> cleanLinks = cleanQuestionLinks(tenantId, questionLinks);
        List<String> cleanIds = cleanLinks.stream().map(QuestionLinkInput::questionId).toList();
        if (unchanged(tenantId, quizId, cleanLinks)) {
            return cleanIds;
        }
        deleteExistingLinks(tenantId, quizId);
        List<QuizQuestion> links = new ArrayList<>(cleanLinks.size());
        for (int index = 0; index < cleanLinks.size(); index += 1) {
            QuestionLinkInput input = cleanLinks.get(index);
            QuizQuestion link = new QuizQuestion();
            link.setTenantId(tenantId);
            link.setQuizId(quizId);
            link.setQuestionId(input.questionId());
            link.setOrderIndex(index);
            link.setSource("manual");
            link.setPoints(input.points());
            link.setSettings(input.settings() == null ? Map.of() : input.settings());
            link.setMetadata(Map.of());
            links.add(link);
        }
        if (!links.isEmpty()) {
            quizQuestionRepository.saveAll(links);
        }
        return cleanIds;
    }

    // An autosave that did not touch the question list must not delete and re-insert every link row.
    private boolean unchanged(String tenantId, String quizId, List<QuestionLinkInput> wanted) {
        List<QuizQuestion> current = quizQuestionRepository.findByTenantIdAndQuizIdOrderByOrderIndexAsc(tenantId, quizId);
        if (current.size() != wanted.size()) {
            return false;
        }
        for (int index = 0; index < current.size(); index += 1) {
            QuizQuestion link = current.get(index);
            QuestionLinkInput input = wanted.get(index);
            if (!input.questionId().equals(link.getQuestionId()) || !"manual".equals(link.getSource())
                    || link.getOrderIndex() == null || link.getOrderIndex() != index
                    || (link.getMetadata() != null && !link.getMetadata().isEmpty())
                    || (input.points() == null ? link.getPoints() != null
                            : link.getPoints() == null || input.points().compareTo(link.getPoints()) != 0)
                    || !java.util.Objects.equals(link.getSettings() == null ? Map.of() : link.getSettings(),
                            input.settings() == null ? Map.of() : input.settings())) {
                return false;
            }
        }
        return true;
    }

    private void deleteExistingLinks(String tenantId, String quizId) {
        jdbcTemplate.update(
                "delete from lms_quiz_questions where tenant_id = ? and quiz_id = ?",
                tenantId,
                quizId
        );
    }

    private List<QuestionLinkInput> cleanQuestionLinks(String tenantId, List<QuestionLinkInput> questionLinks) {
        if (questionLinks == null) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        List<QuestionLinkInput> result = new ArrayList<>();
        List<QuestionLinkInput> candidates = new ArrayList<>();
        for (QuestionLinkInput link : questionLinks) {
            String questionId = link == null ? null : link.questionId();
            if (questionId == null || questionId.isBlank()) {
                continue;
            }
            String normalized = questionId.trim();
            if (!ids.contains(normalized)) {
                ids.add(normalized);
                candidates.add(new QuestionLinkInput(normalized,
                        link.settings() == null ? Map.of() : link.settings(), link.points()));
            }
        }
        if (ids.isEmpty()) return result;
        java.util.Set<String> existingIds = new java.util.HashSet<>(
                questionRepository.findIdsByTenantIdAndIdIn(tenantId, new ArrayList<>(ids)));
        candidates.stream()
                .filter(candidate -> existingIds.contains(candidate.questionId()))
                .forEach(result::add);
        return result;
    }

    public record QuestionLinkInput(String questionId, Map<String, Object> settings, java.math.BigDecimal points) {
    }
}
