package com.erg.ergspringboot.service.quiz.assessment;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LmsAssessmentCurriculumBridge implements AssessmentCurriculumBridge {

    private final CurriculumSubjectRepository subjectRepository;
    private final CurriculumLevelRepository levelRepository;
    private final CurriculumTopicRepository topicRepository;

    public LmsAssessmentCurriculumBridge(
            CurriculumSubjectRepository subjectRepository,
            CurriculumLevelRepository levelRepository,
            CurriculumTopicRepository topicRepository
    ) {
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.topicRepository = topicRepository;
    }

    @Override
    public void validateQuestionPlacement(String tenantId, String subjectId, String levelId, String topicId) {
        validatePlacement(tenantId, subjectId, levelId, topicId, true);
    }

    @Override
    public void validateQuizPlacement(String tenantId, String subjectId, String levelId, String topicId) {
        validatePlacement(tenantId, subjectId, levelId, topicId, false);
    }

    @Override
    public Map<String, Object> snapshotForQuiz(String tenantId, Quiz quiz) {
        if (quiz == null) {
            return Map.of();
        }
        ResolvedPlacement placement = validatePlacement(
                tenantId,
                quiz.getSubjectId(),
                quiz.getLevelId(),
                quiz.getCategoryId(),
                false
        );
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("bridge", "canonical-curriculum");
        snapshot.put("canonicalPending", false);
        if (placement.subject() != null) {
            snapshot.put("subject", ref(placement.subject().getId(), placement.subject().getName(), "curriculum_subjects"));
        }
        if (placement.level() != null) {
            snapshot.put("level", ref(placement.level().getId(), placement.level().getName(), "curriculum_levels"));
        }
        if (placement.topic() != null) {
            snapshot.put("topic", ref(placement.topic().getId(), placement.topic().getName(), "curriculum_topics"));
        }
        return snapshot;
    }

    private ResolvedPlacement validatePlacement(
            String tenantId,
            String subjectId,
            String levelId,
            String topicId,
            boolean requireFullChain
    ) {
        String subjectKey = blankToNull(subjectId);
        String levelKey = blankToNull(levelId);
        String topicKey = blankToNull(topicId);

        if (subjectKey == null && levelKey == null && topicKey == null && !requireFullChain) {
            return new ResolvedPlacement(null, null, null);
        }
        if (subjectKey == null) {
            throw new BadRequestException("subjectId is required for assessment curriculum placement");
        }
        if (levelKey == null && (requireFullChain || topicKey != null)) {
            throw new BadRequestException("levelId is required for assessment curriculum placement");
        }
        if (topicKey == null && requireFullChain) {
            throw new BadRequestException("topicId is required for assessment curriculum placement");
        }

        CurriculumSubject subject = subjectRepository.findByTenantIdAndId(tenantId, subjectKey)
                .orElseThrow(() -> new BadRequestException("subject not found in tenant: " + subjectKey));

        CurriculumLevel level = null;
        if (levelKey != null) {
            level = levelRepository.findByTenantIdAndId(tenantId, levelKey)
                    .orElseThrow(() -> new BadRequestException("level not found in tenant: " + levelKey));
            if (!subjectKey.equals(level.getSubjectId())) {
                throw new BadRequestException("level does not belong to subject");
            }
        }

        CurriculumTopic topic = null;
        if (topicKey != null) {
            topic = topicRepository.findByTenantIdAndId(tenantId, topicKey)
                    .orElseThrow(() -> new BadRequestException("topic not found in tenant: " + topicKey));
            if (level == null || !levelKey.equals(topic.getLevelId())) {
                throw new BadRequestException("topic does not belong to level");
            }
        }

        return new ResolvedPlacement(subject, level, topic);
    }

    private Map<String, Object> ref(String id, String label, String sourceTable) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("label", blankToNull(label) == null ? id : label.trim());
        value.put("sourceTable", sourceTable);
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ResolvedPlacement(CurriculumSubject subject, CurriculumLevel level, CurriculumTopic topic) {
    }
}
