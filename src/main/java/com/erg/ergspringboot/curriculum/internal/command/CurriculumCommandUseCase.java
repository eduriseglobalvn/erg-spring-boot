package com.erg.ergspringboot.curriculum.internal.command;

import com.erg.ergspringboot.curriculum.internal.impact.CurriculumImpactQueryUseCase;
import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumHardDeleteResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumImpactResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumLevelResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumNodeRequest;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumSubjectResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTopicResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns transactional curriculum taxonomy creation, updates, archival and guarded deletion. */
@Service
@Transactional(value = "postgresTransactionManager")
public class CurriculumCommandUseCase {

    private static final String ACTIVE = "active";
    private static final String ARCHIVED = "archived";
    private static final String DRAFT = "draft";
    private static final Set<String> STATUSES = Set.of(ACTIVE, ARCHIVED, DRAFT);

    private final CurriculumSubjectRepository subjects;
    private final CurriculumLevelRepository levels;
    private final CurriculumTopicRepository topics;
    private final CurriculumImpactQueryUseCase impacts;

    public CurriculumCommandUseCase(
            CurriculumSubjectRepository subjects,
            CurriculumLevelRepository levels,
            CurriculumTopicRepository topics,
            CurriculumImpactQueryUseCase impacts) {
        this.subjects = subjects;
        this.levels = levels;
        this.topics = topics;
        this.impacts = impacts;
    }

    public CurriculumSubjectResponse createSubject(
            CurriculumNodeRequest request, UserAccessContext access) {
        String tenantId = tenantId(access);
        requirePayload(request);
        String code = requireCode(request.code());
        if (subjects.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) {
            throw CurriculumException.conflict(
                    "CURRICULUM_CODE_EXISTS", "subject code already exists in tenant");
        }
        CurriculumSubject subject = new CurriculumSubject();
        subject.setTenantId(tenantId);
        subject.setCode(code);
        subject.setName(requireName(request.name()));
        subject.setDescription(blankToNull(request.description()));
        subject.setStatus(statusOrDefault(request.status()));
        subject.setSortOrder(sortOrderOrDefault(request.sortOrder()));
        subject.setCreatedBy(userId(access));
        subject.setUpdatedBy(userId(access));
        return toSubjectResponse(subjects.save(subject));
    }

    public CurriculumSubjectResponse updateSubject(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        CurriculumSubject subject = requireSubject(id, tenantId(access));
        applyNodeUpdate(subject, request, userId(access));
        return toSubjectResponse(subjects.save(subject));
    }

    public CurriculumSubjectResponse archiveSubject(String id, UserAccessContext access) {
        CurriculumSubject subject = requireSubject(id, tenantId(access));
        subject.setStatus(ARCHIVED);
        subject.setUpdatedBy(userId(access));
        return toSubjectResponse(subjects.save(subject));
    }

    public CurriculumHardDeleteResponse hardDeleteSubject(String id, UserAccessContext access) {
        CurriculumSubject subject = requireSubject(id, tenantId(access));
        requireHardDeleteAllowed(impacts.subjectImpact(id, access));
        subjects.delete(subject);
        return new CurriculumHardDeleteResponse(id, "subject", true);
    }

    public CurriculumLevelResponse createLevel(
            String subjectId, CurriculumNodeRequest request, UserAccessContext access) {
        String tenantId = tenantId(access);
        requirePayload(request);
        CurriculumSubject subject = requireSubject(subjectId, tenantId);
        requireActive(subject.getStatus(), "subject", subjectId);
        String code = requireCode(request.code());
        if (levels.existsByTenantIdAndSubjectIdAndCodeIgnoreCase(tenantId, subjectId, code)) {
            throw CurriculumException.conflict(
                    "CURRICULUM_CODE_EXISTS", "level code already exists under subject");
        }
        CurriculumLevel level = new CurriculumLevel();
        level.setTenantId(tenantId);
        level.setSubjectId(subjectId);
        level.setCode(code);
        level.setName(requireName(request.name()));
        level.setDescription(blankToNull(request.description()));
        level.setStatus(statusOrDefault(request.status()));
        level.setSortOrder(sortOrderOrDefault(request.sortOrder()));
        level.setCreatedBy(userId(access));
        level.setUpdatedBy(userId(access));
        return toLevelResponse(levels.save(level));
    }

    public CurriculumLevelResponse updateLevel(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        CurriculumLevel level = requireLevel(id, tenantId(access));
        applyNodeUpdate(level, request, userId(access));
        return toLevelResponse(levels.save(level));
    }

    public CurriculumLevelResponse archiveLevel(String id, UserAccessContext access) {
        CurriculumLevel level = requireLevel(id, tenantId(access));
        level.setStatus(ARCHIVED);
        level.setUpdatedBy(userId(access));
        return toLevelResponse(levels.save(level));
    }

    public CurriculumHardDeleteResponse hardDeleteLevel(String id, UserAccessContext access) {
        CurriculumLevel level = requireLevel(id, tenantId(access));
        requireHardDeleteAllowed(impacts.levelImpact(id, access));
        levels.delete(level);
        return new CurriculumHardDeleteResponse(id, "level", true);
    }

    public CurriculumTopicResponse createTopic(
            String levelId, CurriculumNodeRequest request, UserAccessContext access) {
        String tenantId = tenantId(access);
        requirePayload(request);
        CurriculumLevel level = requireLevel(levelId, tenantId);
        requireActive(level.getStatus(), "level", levelId);
        requireActive(requireSubject(level.getSubjectId(), tenantId).getStatus(),
                "subject", level.getSubjectId());
        String code = requireCode(request.code());
        if (topics.existsByTenantIdAndLevelIdAndCodeIgnoreCase(tenantId, levelId, code)) {
            throw CurriculumException.conflict(
                    "CURRICULUM_CODE_EXISTS", "topic code already exists under level");
        }
        CurriculumTopic topic = new CurriculumTopic();
        topic.setTenantId(tenantId);
        topic.setLevelId(levelId);
        topic.setCode(code);
        topic.setName(requireName(request.name()));
        topic.setDescription(blankToNull(request.description()));
        topic.setStatus(statusOrDefault(request.status()));
        topic.setSortOrder(sortOrderOrDefault(request.sortOrder()));
        topic.setCreatedBy(userId(access));
        topic.setUpdatedBy(userId(access));
        return toTopicResponse(topics.save(topic), level.getSubjectId());
    }

    public CurriculumTopicResponse updateTopic(
            String id, CurriculumNodeRequest request, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumTopic topic = requireTopic(id, tenantId);
        applyNodeUpdate(topic, request, userId(access));
        CurriculumLevel level = requireLevel(topic.getLevelId(), tenantId);
        return toTopicResponse(topics.save(topic), level.getSubjectId());
    }

    public CurriculumTopicResponse archiveTopic(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumTopic topic = requireTopic(id, tenantId);
        topic.setStatus(ARCHIVED);
        topic.setUpdatedBy(userId(access));
        CurriculumLevel level = requireLevel(topic.getLevelId(), tenantId);
        return toTopicResponse(topics.save(topic), level.getSubjectId());
    }

    public CurriculumHardDeleteResponse hardDeleteTopic(String id, UserAccessContext access) {
        CurriculumTopic topic = requireTopic(id, tenantId(access));
        requireHardDeleteAllowed(impacts.topicImpact(id, access));
        topics.delete(topic);
        return new CurriculumHardDeleteResponse(id, "topic", true);
    }

    private void applyNodeUpdate(
            CurriculumSubject subject, CurriculumNodeRequest request, String userId) {
        requirePayload(request);
        if (blankToNull(request.code()) != null) {
            subject.setCode(requireCode(request.code()));
        }
        if (blankToNull(request.name()) != null) {
            subject.setName(requireName(request.name()));
        }
        if (request.description() != null) {
            subject.setDescription(blankToNull(request.description()));
        }
        if (request.status() != null) {
            subject.setStatus(normalizeStatus(request.status()));
        }
        if (request.sortOrder() != null) {
            subject.setSortOrder(request.sortOrder());
        }
        subject.setUpdatedBy(userId);
    }

    private void applyNodeUpdate(
            CurriculumLevel level, CurriculumNodeRequest request, String userId) {
        requirePayload(request);
        if (blankToNull(request.code()) != null) {
            level.setCode(requireCode(request.code()));
        }
        if (blankToNull(request.name()) != null) {
            level.setName(requireName(request.name()));
        }
        if (request.description() != null) {
            level.setDescription(blankToNull(request.description()));
        }
        if (request.status() != null) {
            level.setStatus(normalizeStatus(request.status()));
        }
        if (request.sortOrder() != null) {
            level.setSortOrder(request.sortOrder());
        }
        level.setUpdatedBy(userId);
    }

    private void applyNodeUpdate(
            CurriculumTopic topic, CurriculumNodeRequest request, String userId) {
        requirePayload(request);
        if (blankToNull(request.code()) != null) {
            topic.setCode(requireCode(request.code()));
        }
        if (blankToNull(request.name()) != null) {
            topic.setName(requireName(request.name()));
        }
        if (request.description() != null) {
            topic.setDescription(blankToNull(request.description()));
        }
        if (request.status() != null) {
            topic.setStatus(normalizeStatus(request.status()));
        }
        if (request.sortOrder() != null) {
            topic.setSortOrder(request.sortOrder());
        }
        topic.setUpdatedBy(userId);
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

    private void requireHardDeleteAllowed(CurriculumImpactResponse impact) {
        if (!impact.canHardDelete()) {
            throw CurriculumException.conflict(
                    "CURRICULUM_HARD_DELETE_REJECTED",
                    "hard delete is only allowed when impact report has no dependencies");
        }
    }

    private void requirePayload(CurriculumNodeRequest request) {
        if (request == null) {
            throw CurriculumException.badRequest(
                    "CURRICULUM_INVALID_REQUEST", "request body is required");
        }
    }

    private String requireCode(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw CurriculumException.badRequest("CURRICULUM_CODE_REQUIRED", "code is required");
        }
        return normalized;
    }

    private String requireName(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw CurriculumException.badRequest("CURRICULUM_NAME_REQUIRED", "name is required");
        }
        return normalized;
    }

    private String statusOrDefault(String status) {
        String normalized = blankToNull(status);
        return normalized == null ? ACTIVE : normalizeStatus(normalized);
    }

    private String normalizeStatus(String status) {
        String normalized = blankToNull(status);
        if (normalized == null) {
            throw CurriculumException.badRequest("CURRICULUM_STATUS_REQUIRED", "status is required");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(lower)) {
            throw CurriculumException.badRequest(
                    "CURRICULUM_STATUS_INVALID", "status must be one of: active, archived, draft");
        }
        return lower;
    }

    private Integer sortOrderOrDefault(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String tenantId(UserAccessContext access) {
        return access == null || blankToNull(access.tenantId()) == null ? "erg" : access.tenantId().trim();
    }

    private String userId(UserAccessContext access) {
        return access == null || blankToNull(access.userId()) == null ? "system" : access.userId().trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CurriculumSubjectResponse toSubjectResponse(CurriculumSubject subject) {
        return new CurriculumSubjectResponse(
                subject.getId(), subject.getTenantId(), subject.getCode(), subject.getName(),
                subject.getDescription(), subject.getStatus(), subject.getSortOrder(),
                subject.getCreatedBy(), subject.getUpdatedBy(), subject.getCreatedAt(), subject.getUpdatedAt());
    }

    private CurriculumLevelResponse toLevelResponse(CurriculumLevel level) {
        return new CurriculumLevelResponse(
                level.getId(), level.getTenantId(), level.getSubjectId(), level.getCode(), level.getName(),
                level.getDescription(), level.getStatus(), level.getSortOrder(), level.getCreatedBy(),
                level.getUpdatedBy(), level.getCreatedAt(), level.getUpdatedAt());
    }

    private CurriculumTopicResponse toTopicResponse(CurriculumTopic topic, String subjectId) {
        return new CurriculumTopicResponse(
                topic.getId(), topic.getTenantId(), topic.getLevelId(), subjectId, topic.getCode(),
                topic.getName(), topic.getDescription(), topic.getStatus(), topic.getSortOrder(),
                topic.getCreatedBy(), topic.getUpdatedBy(), topic.getCreatedAt(), topic.getUpdatedAt());
    }
}
