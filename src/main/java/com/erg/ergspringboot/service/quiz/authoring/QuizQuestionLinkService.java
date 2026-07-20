package com.erg.ergspringboot.service.quiz.authoring;

import com.erg.ergspringboot.model.lms.QuizQuestion;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizQuestionRepository;
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
        deleteExistingLinks(tenantId, quizId);
        List<String> cleanIds = new ArrayList<>();
        List<QuizQuestion> links = new ArrayList<>(cleanLinks.size());
        for (int index = 0; index < cleanLinks.size(); index += 1) {
            QuestionLinkInput input = cleanLinks.get(index);
            cleanIds.add(input.questionId());
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
        java.util.Set<String> existingIds = questionRepository.findByTenantIdAndIdIn(tenantId, new ArrayList<>(ids))
                .stream().map(com.erg.ergspringboot.model.lms.Question::getId)
                .collect(java.util.stream.Collectors.toSet());
        candidates.stream()
                .filter(candidate -> existingIds.contains(candidate.questionId()))
                .forEach(result::add);
        return result;
    }

    public record QuestionLinkInput(String questionId, Map<String, Object> settings, java.math.BigDecimal points) {
    }
}
