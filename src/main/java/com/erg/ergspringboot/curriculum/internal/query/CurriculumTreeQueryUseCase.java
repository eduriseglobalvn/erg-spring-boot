package com.erg.ergspringboot.curriculum.internal.query;

import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumLevelResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumLevelTree;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumSubjectResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumSubjectTree;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTopicResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTreeResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the bounded subject-level-topic tree query and stable response composition. */
@Service
public class CurriculumTreeQueryUseCase {

    private static final String ACTIVE = "active";
    private static final String ARCHIVED = "archived";
    private static final String DRAFT = "draft";
    private static final Set<String> STATUSES = Set.of(ACTIVE, ARCHIVED, DRAFT);
    private static final int TREE_NODE_LIMIT = 500;

    private final CurriculumSubjectRepository subjects;
    private final CurriculumLevelRepository levels;
    private final CurriculumTopicRepository topics;

    public CurriculumTreeQueryUseCase(
            CurriculumSubjectRepository subjects,
            CurriculumLevelRepository levels,
            CurriculumTopicRepository topics) {
        this.subjects = subjects;
        this.levels = levels;
        this.topics = topics;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public CurriculumTreeResponse tree(String status, UserAccessContext access) {
        String tenantId = tenantId(access);
        String normalizedStatus = blankToNull(status) == null ? ACTIVE : normalizeStatus(status);
        PageRequest treeLimit = PageRequest.of(0, TREE_NODE_LIMIT + 1);
        List<CurriculumSubject> subjectNodes = normalizedStatus == null
                ? subjects.findByTenantIdOrderBySortOrderAscNameAsc(tenantId, treeLimit)
                : subjects.findByTenantIdAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                        tenantId, normalizedStatus, treeLimit);
        assertTreeBound(subjectNodes, "subjects");
        if (subjectNodes.isEmpty()) {
            return new CurriculumTreeResponse(List.of());
        }

        List<String> subjectIds = subjectNodes.stream().map(CurriculumSubject::getId).toList();
        List<CurriculumLevel> levelNodes = normalizedStatus == null
                ? levels.findByTenantIdAndSubjectIdInOrderBySortOrderAscNameAsc(
                        tenantId, subjectIds, treeLimit)
                : levels.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                        tenantId, subjectIds, normalizedStatus, treeLimit);
        assertTreeBound(levelNodes, "levels");
        List<String> levelIds = levelNodes.stream().map(CurriculumLevel::getId).toList();
        List<CurriculumTopic> topicNodes = levelIds.isEmpty()
                ? List.of()
                : normalizedStatus == null
                ? topics.findByTenantIdAndLevelIdInOrderBySortOrderAscNameAsc(
                        tenantId, levelIds, treeLimit)
                : topics.findByTenantIdAndLevelIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                        tenantId, levelIds, normalizedStatus, treeLimit);
        assertTreeBound(topicNodes, "topics");

        Map<String, List<CurriculumTopic>> topicsByLevel = topicNodes.stream()
                .collect(Collectors.groupingBy(
                        CurriculumTopic::getLevelId, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<CurriculumLevel>> levelsBySubject = levelNodes.stream()
                .collect(Collectors.groupingBy(
                        CurriculumLevel::getSubjectId, LinkedHashMap::new, Collectors.toList()));

        List<CurriculumSubjectTree> tree = subjectNodes.stream()
                .map(subject -> subjectTree(
                        subject, levelsBySubject.getOrDefault(subject.getId(), List.of()), topicsByLevel))
                .toList();
        return new CurriculumTreeResponse(tree);
    }

    private CurriculumSubjectTree subjectTree(
            CurriculumSubject subject,
            List<CurriculumLevel> levelNodes,
            Map<String, List<CurriculumTopic>> topicsByLevel) {
        List<CurriculumLevelTree> levelTrees = levelNodes.stream()
                .sorted(nodeComparator())
                .map(level -> new CurriculumLevelTree(
                        toLevelResponse(level),
                        topicsByLevel.getOrDefault(level.getId(), List.of()).stream()
                                .sorted(nodeComparator())
                                .map(topic -> toTopicResponse(topic, level.getSubjectId()))
                                .toList()))
                .toList();
        return new CurriculumSubjectTree(toSubjectResponse(subject), levelTrees);
    }

    private <T> Comparator<T> nodeComparator() {
        return Comparator.comparing(
                        (T node) -> sortOrder(node), Comparator.nullsLast(Integer::compareTo))
                .thenComparing(node -> name(node).toLowerCase(Locale.ROOT));
    }

    private Integer sortOrder(Object node) {
        if (node instanceof CurriculumSubject subject) {
            return subject.getSortOrder();
        }
        if (node instanceof CurriculumLevel level) {
            return level.getSortOrder();
        }
        if (node instanceof CurriculumTopic topic) {
            return topic.getSortOrder();
        }
        return 0;
    }

    private String name(Object node) {
        if (node instanceof CurriculumSubject subject) {
            return subject.getName();
        }
        if (node instanceof CurriculumLevel level) {
            return level.getName();
        }
        if (node instanceof CurriculumTopic topic) {
            return topic.getName();
        }
        return "";
    }

    private void assertTreeBound(List<?> nodes, String type) {
        if (nodes.size() > TREE_NODE_LIMIT) {
            throw CurriculumException.badRequest(
                    "CURRICULUM_TREE_TOO_LARGE",
                    "curriculum tree exceeds " + TREE_NODE_LIMIT + " " + type
                            + "; request a narrower scope");
        }
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

    private String tenantId(UserAccessContext access) {
        if (access == null || blankToNull(access.tenantId()) == null) {
            return "erg";
        }
        return access.tenantId().trim();
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
