package com.erg.ergspringboot.curriculum.internal.validation;

import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTopicResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns tenant-scoped subject-level-topic relationship and active-state validation. */
@Service
public class CurriculumChainValidationUseCase {

    private static final String ACTIVE = "active";

    private final CurriculumSubjectRepository subjects;
    private final CurriculumLevelRepository levels;
    private final CurriculumTopicRepository topics;

    public CurriculumChainValidationUseCase(
            CurriculumSubjectRepository subjects,
            CurriculumLevelRepository levels,
            CurriculumTopicRepository topics) {
        this.subjects = subjects;
        this.levels = levels;
        this.topics = topics;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public CurriculumTopicResponse validate(
            String subjectId, String levelId, String topicId, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumSubject subject = requireSubject(subjectId, tenantId);
        CurriculumLevel level = requireLevel(levelId, tenantId);
        CurriculumTopic topic = requireTopic(topicId, tenantId);
        if (!subject.getId().equals(level.getSubjectId())) {
            throw CurriculumException.badRequest(
                    "CURRICULUM_CHAIN_MISMATCH", "level does not belong to subject");
        }
        if (!level.getId().equals(topic.getLevelId())) {
            throw CurriculumException.badRequest(
                    "CURRICULUM_CHAIN_MISMATCH", "topic does not belong to level");
        }
        requireActive(subject.getStatus(), "subject", subjectId);
        requireActive(level.getStatus(), "level", levelId);
        requireActive(topic.getStatus(), "topic", topicId);
        return toTopicResponse(topic, subjectId);
    }

    private CurriculumSubject requireSubject(String id, String tenantId) {
        return subjects.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> CurriculumException.notFound("subject", id));
    }

    private CurriculumLevel requireLevel(String id, String tenantId) {
        return levels.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> CurriculumException.notFound("level", id));
    }

    private CurriculumTopic requireTopic(String id, String tenantId) {
        return topics.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> CurriculumException.notFound("topic", id));
    }

    private void requireActive(String status, String nodeType, String id) {
        if (!ACTIVE.equalsIgnoreCase(status)) {
            throw CurriculumException.badRequest(
                    "CURRICULUM_PARENT_NOT_ACTIVE", nodeType + " must be active: " + id);
        }
    }

    private String tenantId(UserAccessContext access) {
        return access == null || blankToNull(access.tenantId()) == null ? "erg" : access.tenantId().trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CurriculumTopicResponse toTopicResponse(CurriculumTopic topic, String subjectId) {
        return new CurriculumTopicResponse(
                topic.getId(), topic.getTenantId(), topic.getLevelId(), subjectId, topic.getCode(),
                topic.getName(), topic.getDescription(), topic.getStatus(), topic.getSortOrder(),
                topic.getCreatedBy(), topic.getUpdatedBy(), topic.getCreatedAt(), topic.getUpdatedAt());
    }
}
