package com.erg.ergspringboot.service.curriculum;

import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumHardDeleteResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumImpactCounts;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumImpactResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumLevelResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumLevelTree;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumNodeRequest;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumSubjectResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumSubjectTree;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTopicResponse;
import com.erg.ergspringboot.service.curriculum.CurriculumDtos.CurriculumTreeResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager")
public class CurriculumService {

    public static final String ACTIVE = "active";
    public static final String ARCHIVED = "archived";
    public static final String DRAFT = "draft";
    private static final Set<String> STATUSES = Set.of(ACTIVE, ARCHIVED, DRAFT);
    private static final int TREE_NODE_LIMIT = 500;

    private final CurriculumSubjectRepository subjectRepository;
    private final CurriculumLevelRepository levelRepository;
    private final CurriculumTopicRepository topicRepository;
    private final TopicContentItemRepository topicContentItemRepository;
    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final QuizVersionRepository quizVersionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;

    public CurriculumService(
            CurriculumSubjectRepository subjectRepository,
            CurriculumLevelRepository levelRepository,
            CurriculumTopicRepository topicRepository,
            TopicContentItemRepository topicContentItemRepository,
            QuestionRepository questionRepository,
            QuizRepository quizRepository,
            QuizVersionRepository quizVersionRepository,
            AssignmentRepository assignmentRepository,
            AttemptRepository attemptRepository
    ) {
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.topicRepository = topicRepository;
        this.topicContentItemRepository = topicContentItemRepository;
        this.questionRepository = questionRepository;
        this.quizRepository = quizRepository;
        this.quizVersionRepository = quizVersionRepository;
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    @Cacheable(cacheNames = "curriculumTree", key = "{#access.tenantId(), #status == null ? 'active' : #status.toLowerCase()}")
    public CurriculumTreeResponse listTree(String status, UserAccessContext access) {
        String tenantId = tenantId(access);
        String normalizedStatus = blankToNull(status) == null ? ACTIVE : normalizeStatus(status);
        PageRequest treeLimit = PageRequest.of(0, TREE_NODE_LIMIT + 1);
        List<CurriculumSubject> subjects = normalizedStatus == null
                ? subjectRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId, treeLimit)
                : subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                        tenantId, normalizedStatus, treeLimit);
        assertTreeBound(subjects, "subjects");
        if (subjects.isEmpty()) {
            return new CurriculumTreeResponse(List.of());
        }

        List<String> subjectIds = subjects.stream().map(CurriculumSubject::getId).toList();
        List<CurriculumLevel> levels = normalizedStatus == null
                ? levelRepository.findByTenantIdAndSubjectIdInOrderBySortOrderAscNameAsc(
                        tenantId, subjectIds, treeLimit)
                : levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                        tenantId,
                        subjectIds,
                        normalizedStatus,
                        treeLimit
                );
        assertTreeBound(levels, "levels");
        List<String> levelIds = levels.stream().map(CurriculumLevel::getId).toList();
        List<CurriculumTopic> topics = levelIds.isEmpty()
                ? List.of()
                : normalizedStatus == null
                        ? topicRepository.findByTenantIdAndLevelIdInOrderBySortOrderAscNameAsc(
                                tenantId, levelIds, treeLimit)
                        : topicRepository.findByTenantIdAndLevelIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                                tenantId,
                                levelIds,
                                normalizedStatus,
                                treeLimit
                        );
        assertTreeBound(topics, "topics");

        Map<String, List<CurriculumTopic>> topicsByLevel = topics.stream()
                .collect(Collectors.groupingBy(CurriculumTopic::getLevelId, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<CurriculumLevel>> levelsBySubject = levels.stream()
                .collect(Collectors.groupingBy(CurriculumLevel::getSubjectId, LinkedHashMap::new, Collectors.toList()));

        List<CurriculumSubjectTree> tree = subjects.stream()
                .map(subject -> subjectTree(subject, levelsBySubject.getOrDefault(subject.getId(), List.of()), topicsByLevel))
                .toList();
        return new CurriculumTreeResponse(tree);
    }

    private void assertTreeBound(List<?> nodes, String type) {
        if (nodes.size() > TREE_NODE_LIMIT) {
            throw CurriculumException.badRequest(
                    "CURRICULUM_TREE_TOO_LARGE",
                    "curriculum tree exceeds " + TREE_NODE_LIMIT + " " + type + "; request a narrower scope");
        }
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumSubjectResponse createSubject(CurriculumNodeRequest request, UserAccessContext access) {
        String tenantId = tenantId(access);
        requireCreatePayload(request);
        String code = requireCode(request.code());
        if (subjectRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) {
            throw CurriculumException.conflict("CURRICULUM_CODE_EXISTS", "subject code already exists in tenant");
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
        return toSubjectResponse(subjectRepository.save(subject));
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumSubjectResponse updateSubject(String id, CurriculumNodeRequest request, UserAccessContext access) {
        CurriculumSubject subject = requireSubject(id, tenantId(access));
        applyNodeUpdate(subject, request, userId(access));
        return toSubjectResponse(subjectRepository.save(subject));
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumSubjectResponse archiveSubject(String id, UserAccessContext access) {
        CurriculumSubject subject = requireSubject(id, tenantId(access));
        subject.setStatus(ARCHIVED);
        subject.setUpdatedBy(userId(access));
        return toSubjectResponse(subjectRepository.save(subject));
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumHardDeleteResponse hardDeleteSubject(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumSubject subject = requireSubject(id, tenantId);
        CurriculumImpactResponse impact = subjectImpact(id, access);
        requireHardDeleteAllowed(subject.getStatus(), impact);
        subjectRepository.delete(subject);
        return new CurriculumHardDeleteResponse(id, "subject", true);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumLevelResponse createLevel(String subjectId, CurriculumNodeRequest request, UserAccessContext access) {
        String tenantId = tenantId(access);
        requireCreatePayload(request);
        CurriculumSubject subject = requireSubject(subjectId, tenantId);
        requireActive(subject.getStatus(), "subject", subjectId);
        String code = requireCode(request.code());
        if (levelRepository.existsByTenantIdAndSubjectIdAndCodeIgnoreCase(tenantId, subjectId, code)) {
            throw CurriculumException.conflict("CURRICULUM_CODE_EXISTS", "level code already exists under subject");
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
        return toLevelResponse(levelRepository.save(level));
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumLevelResponse updateLevel(String id, CurriculumNodeRequest request, UserAccessContext access) {
        CurriculumLevel level = requireLevel(id, tenantId(access));
        applyNodeUpdate(level, request, userId(access));
        return toLevelResponse(levelRepository.save(level));
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumLevelResponse archiveLevel(String id, UserAccessContext access) {
        CurriculumLevel level = requireLevel(id, tenantId(access));
        level.setStatus(ARCHIVED);
        level.setUpdatedBy(userId(access));
        return toLevelResponse(levelRepository.save(level));
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumHardDeleteResponse hardDeleteLevel(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumLevel level = requireLevel(id, tenantId);
        CurriculumImpactResponse impact = levelImpact(id, access);
        requireHardDeleteAllowed(level.getStatus(), impact);
        levelRepository.delete(level);
        return new CurriculumHardDeleteResponse(id, "level", true);
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumTopicResponse createTopic(String levelId, CurriculumNodeRequest request, UserAccessContext access) {
        String tenantId = tenantId(access);
        requireCreatePayload(request);
        CurriculumLevel level = requireLevel(levelId, tenantId);
        requireActive(level.getStatus(), "level", levelId);
        requireActive(requireSubject(level.getSubjectId(), tenantId).getStatus(), "subject", level.getSubjectId());
        String code = requireCode(request.code());
        if (topicRepository.existsByTenantIdAndLevelIdAndCodeIgnoreCase(tenantId, levelId, code)) {
            throw CurriculumException.conflict("CURRICULUM_CODE_EXISTS", "topic code already exists under level");
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
        return toTopicResponse(topicRepository.save(topic), level.getSubjectId());
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumTopicResponse updateTopic(String id, CurriculumNodeRequest request, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumTopic topic = requireTopic(id, tenantId);
        applyNodeUpdate(topic, request, userId(access));
        CurriculumLevel level = requireLevel(topic.getLevelId(), tenantId);
        return toTopicResponse(topicRepository.save(topic), level.getSubjectId());
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumTopicResponse archiveTopic(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumTopic topic = requireTopic(id, tenantId);
        topic.setStatus(ARCHIVED);
        topic.setUpdatedBy(userId(access));
        CurriculumLevel level = requireLevel(topic.getLevelId(), tenantId);
        return toTopicResponse(topicRepository.save(topic), level.getSubjectId());
    }

    @CacheEvict(cacheNames = {"curriculumTree", "learningResourceLibrary"}, allEntries = true)
    public CurriculumHardDeleteResponse hardDeleteTopic(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumTopic topic = requireTopic(id, tenantId);
        CurriculumImpactResponse impact = topicImpact(id, access);
        requireHardDeleteAllowed(topic.getStatus(), impact);
        topicRepository.delete(topic);
        return new CurriculumHardDeleteResponse(id, "topic", true);
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public CurriculumImpactResponse subjectImpact(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumSubject subject = requireSubject(id, tenantId);
        long childLevels = levelRepository.countByTenantIdAndSubjectId(tenantId, id);
        List<String> topicIds = topicIdsForSubject(tenantId, id);
        CurriculumImpactCounts counts = new CurriculumImpactCounts(
                questionRepository.countImpactByTenantAndSubject(tenantId, id),
                quizRepository.countImpactByTenantAndSubject(tenantId, id),
                quizVersionRepository.countPublishedImpactByTenantAndSubject(tenantId, id),
                contentItemCount(tenantId, topicIds),
                assignmentRepository.countImpactByTenantAndSubject(tenantId, id),
                attemptRepository.countImpactByTenantAndSubject(tenantId, id),
                childLevels,
                topicIds.size()
        );
        return impact(subject.getId(), "subject", subject.getStatus(), counts);
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public CurriculumImpactResponse levelImpact(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumLevel level = requireLevel(id, tenantId);
        long childTopics = topicRepository.countByTenantIdAndLevelId(tenantId, id);
        List<String> topicIds = topicIdsForLevel(tenantId, id);
        CurriculumImpactCounts counts = new CurriculumImpactCounts(
                questionRepository.countImpactByTenantAndLevel(tenantId, id),
                quizRepository.countImpactByTenantAndLevel(tenantId, id),
                quizVersionRepository.countPublishedImpactByTenantAndLevel(tenantId, id),
                contentItemCount(tenantId, topicIds),
                assignmentRepository.countImpactByTenantAndLevel(tenantId, id),
                attemptRepository.countImpactByTenantAndLevel(tenantId, id),
                0,
                childTopics
        );
        return impact(level.getId(), "level", level.getStatus(), counts);
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public CurriculumImpactResponse topicImpact(String id, UserAccessContext access) {
        String tenantId = tenantId(access);
        CurriculumTopic topic = requireTopic(id, tenantId);
        CurriculumImpactCounts counts = new CurriculumImpactCounts(
                questionRepository.countImpactByTenantAndTopic(tenantId, id),
                quizRepository.countImpactByTenantAndTopic(tenantId, id),
                quizVersionRepository.countPublishedImpactByTenantAndTopic(tenantId, id),
                topicContentItemRepository.countByTenantIdAndTopicId(tenantId, id),
                assignmentRepository.countImpactByTenantAndTopic(tenantId, id),
                attemptRepository.countImpactByTenantAndTopic(tenantId, id),
                0,
                0
        );
        return impact(topic.getId(), "topic", topic.getStatus(), counts);
    }

    public CurriculumTopicResponse validateTopicChain(
            String subjectId,
            String levelId,
            String topicId,
            UserAccessContext access
    ) {
        String tenantId = tenantId(access);
        CurriculumSubject subject = requireSubject(subjectId, tenantId);
        CurriculumLevel level = requireLevel(levelId, tenantId);
        CurriculumTopic topic = requireTopic(topicId, tenantId);
        if (!subject.getId().equals(level.getSubjectId())) {
            throw CurriculumException.badRequest(
                    "CURRICULUM_CHAIN_MISMATCH",
                    "level does not belong to subject"
            );
        }
        if (!level.getId().equals(topic.getLevelId())) {
            throw CurriculumException.badRequest(
                    "CURRICULUM_CHAIN_MISMATCH",
                    "topic does not belong to level"
            );
        }
        requireActive(subject.getStatus(), "subject", subjectId);
        requireActive(level.getStatus(), "level", levelId);
        requireActive(topic.getStatus(), "topic", topicId);
        return toTopicResponse(topic, subjectId);
    }

    private CurriculumSubjectTree subjectTree(
            CurriculumSubject subject,
            List<CurriculumLevel> levels,
            Map<String, List<CurriculumTopic>> topicsByLevel
    ) {
        List<CurriculumLevelTree> levelTrees = levels.stream()
                .sorted(nodeComparator())
                .map(level -> new CurriculumLevelTree(
                        toLevelResponse(level),
                        topicsByLevel.getOrDefault(level.getId(), List.of()).stream()
                                .sorted(nodeComparator())
                                .map(topic -> toTopicResponse(topic, level.getSubjectId()))
                                .toList()
                ))
                .toList();
        return new CurriculumSubjectTree(toSubjectResponse(subject), levelTrees);
    }

    private <T> Comparator<T> nodeComparator() {
        return Comparator
                .comparing((T node) -> sortOrder(node), Comparator.nullsLast(Integer::compareTo))
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

    private CurriculumImpactResponse impact(
            String id,
            String nodeType,
            String status,
            CurriculumImpactCounts counts
    ) {
        boolean noDependencies = counts.questions() == 0
                && counts.quizzes() == 0
                && counts.publishedQuizVersions() == 0
                && counts.contentItems() == 0
                && counts.assignments() == 0
                && counts.studentAttempts() == 0
                && counts.childLevels() == 0
                && counts.childTopics() == 0;
        // An empty taxonomy node is safe to remove regardless of its current publication status.
        // Nodes with children or learning references remain archive-only.
        boolean canHardDelete = noDependencies;
        List<String> recommended = new ArrayList<>();
        if (canHardDelete) {
            recommended.add("HARD_DELETE");
        }
        if (!ARCHIVED.equalsIgnoreCase(status)) {
            recommended.add("ARCHIVE");
        }
        recommended.add("REASSIGN");
        recommended.add("MERGE");
        return new CurriculumImpactResponse(
                id,
                nodeType,
                canHardDelete,
                !ARCHIVED.equalsIgnoreCase(status),
                counts,
                recommended,
                "Impact counts include curriculum children, topic content placements, LMS questions/quizzes, published quiz versions, assignments, and attempts from existing relationships."
        );
    }

    private List<String> topicIdsForSubject(String tenantId, String subjectId) {
        List<CurriculumLevel> levels = levelRepository.findByTenantIdAndSubjectIdInOrderBySortOrderAscNameAsc(
                tenantId,
                List.of(subjectId)
        );
        List<String> levelIds = levels.stream().map(CurriculumLevel::getId).toList();
        return topicIdsForLevels(tenantId, levelIds);
    }

    private List<String> topicIdsForLevel(String tenantId, String levelId) {
        return topicIdsForLevels(tenantId, List.of(levelId));
    }

    private List<String> topicIdsForLevels(String tenantId, List<String> levelIds) {
        if (levelIds.isEmpty()) {
            return List.of();
        }
        return topicRepository.findByTenantIdAndLevelIdInOrderBySortOrderAscNameAsc(tenantId, levelIds).stream()
                .map(CurriculumTopic::getId)
                .toList();
    }

    private long contentItemCount(String tenantId, List<String> topicIds) {
        if (topicIds.isEmpty()) {
            return 0;
        }
        return topicContentItemRepository.countByTenantIdAndTopicIdIn(tenantId, topicIds);
    }

    private void requireHardDeleteAllowed(String status, CurriculumImpactResponse impact) {
        if (!impact.canHardDelete()) {
            throw CurriculumException.conflict(
                    "CURRICULUM_HARD_DELETE_REJECTED",
                    "hard delete is only allowed when impact report has no dependencies"
            );
        }
    }

    private CurriculumSubject requireSubject(String id, String tenantId) {
        return subjectRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> CurriculumException.notFound("subject", id));
    }

    private CurriculumLevel requireLevel(String id, String tenantId) {
        return levelRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> CurriculumException.notFound("level", id));
    }

    private CurriculumTopic requireTopic(String id, String tenantId) {
        return topicRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> CurriculumException.notFound("topic", id));
    }

    private void requireActive(String status, String nodeType, String id) {
        if (!ACTIVE.equalsIgnoreCase(status)) {
            throw CurriculumException.badRequest(
                    "CURRICULUM_PARENT_NOT_ACTIVE",
                    nodeType + " must be active: " + id
            );
        }
    }

    private void requireCreatePayload(CurriculumNodeRequest request) {
        if (request == null) {
            throw CurriculumException.badRequest("CURRICULUM_INVALID_REQUEST", "request body is required");
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

    private void applyNodeUpdate(CurriculumSubject subject, CurriculumNodeRequest request, String userId) {
        requireUpdatePayload(request);
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

    private void applyNodeUpdate(CurriculumLevel level, CurriculumNodeRequest request, String userId) {
        requireUpdatePayload(request);
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

    private void applyNodeUpdate(CurriculumTopic topic, CurriculumNodeRequest request, String userId) {
        requireUpdatePayload(request);
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

    private void requireUpdatePayload(CurriculumNodeRequest request) {
        if (request == null) {
            throw CurriculumException.badRequest("CURRICULUM_INVALID_REQUEST", "request body is required");
        }
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
                    "CURRICULUM_STATUS_INVALID",
                    "status must be one of: active, archived, draft"
            );
        }
        return lower;
    }

    private Integer sortOrderOrDefault(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String tenantId(UserAccessContext access) {
        if (access == null || blankToNull(access.tenantId()) == null) {
            return "erg";
        }
        return access.tenantId().trim();
    }

    private String userId(UserAccessContext access) {
        if (access == null || blankToNull(access.userId()) == null) {
            return "system";
        }
        return access.userId().trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CurriculumSubjectResponse toSubjectResponse(CurriculumSubject subject) {
        return new CurriculumSubjectResponse(
                subject.getId(),
                subject.getTenantId(),
                subject.getCode(),
                subject.getName(),
                subject.getDescription(),
                subject.getStatus(),
                subject.getSortOrder(),
                subject.getCreatedBy(),
                subject.getUpdatedBy(),
                subject.getCreatedAt(),
                subject.getUpdatedAt()
        );
    }

    private CurriculumLevelResponse toLevelResponse(CurriculumLevel level) {
        return new CurriculumLevelResponse(
                level.getId(),
                level.getTenantId(),
                level.getSubjectId(),
                level.getCode(),
                level.getName(),
                level.getDescription(),
                level.getStatus(),
                level.getSortOrder(),
                level.getCreatedBy(),
                level.getUpdatedBy(),
                level.getCreatedAt(),
                level.getUpdatedAt()
        );
    }

    private CurriculumTopicResponse toTopicResponse(CurriculumTopic topic, String subjectId) {
        return new CurriculumTopicResponse(
                topic.getId(),
                topic.getTenantId(),
                topic.getLevelId(),
                subjectId,
                topic.getCode(),
                topic.getName(),
                topic.getDescription(),
                topic.getStatus(),
                topic.getSortOrder(),
                topic.getCreatedBy(),
                topic.getUpdatedBy(),
                topic.getCreatedAt(),
                topic.getUpdatedAt()
        );
    }
}

