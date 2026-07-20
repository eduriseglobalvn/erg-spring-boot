package com.erg.ergspringboot.service.learningcontent;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.learningcontent.LearningResourceLibraryResponses.LearningRecentResourceResponse;
import com.erg.ergspringboot.common.response.learningcontent.LearningResourceLibraryResponses.LearningResourceCardResponse;
import com.erg.ergspringboot.common.response.learningcontent.LearningResourceLibraryResponses.LearningResourceLibraryResponse;
import com.erg.ergspringboot.common.response.learningcontent.LearningResourceLibraryResponses.LearningResourceProgressResponse;
import com.erg.ergspringboot.common.response.learningcontent.LearningResourceLibraryResponses.LearningResourceTaxonomyNodeResponse;
import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository.LearningResourceCardProjection;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository.TopicContentCountProjection;
import com.erg.ergspringboot.exception.BadRequestException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LearningResourceLibraryReadService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int HARD_MAX_PAGE_SIZE = 50;
    /** Prevents a taxonomy request from materializing an entire tenant curriculum tree. */
    public static final int HARD_MAX_TAXONOMY_NODES_PER_LEVEL = 500;
    private static final String ACTIVE = "active";

    private final CurriculumSubjectRepository subjectRepository;
    private final CurriculumLevelRepository levelRepository;
    private final CurriculumTopicRepository topicRepository;
    private final TopicContentItemRepository placementRepository;
    private final ContentItemRepository contentItemRepository;

    public LearningResourceLibraryReadService(
            CurriculumSubjectRepository subjectRepository,
            CurriculumLevelRepository levelRepository,
            CurriculumTopicRepository topicRepository,
            TopicContentItemRepository placementRepository,
            ContentItemRepository contentItemRepository
    ) {
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.topicRepository = topicRepository;
        this.placementRepository = placementRepository;
        this.contentItemRepository = contentItemRepository;
    }

    @Cacheable(
            cacheNames = "learningResourceLibrary",
            key = "{#tenantId, #educationUnitId, #userId, #academicYear, #subjectId, #gradeId, #categoryId, #sectionId, #topicId, #status, #visibility, #page, #size}"
    )
    public LearningResourceLibraryResponse learningResourceLibrary(
            String tenantId,
            String educationUnitId,
            String userId,
            String academicYear,
            String subjectId,
            String gradeId,
            String categoryId,
            String sectionId,
            String topicId,
            String status,
            String visibility,
            Integer page,
            Integer size
    ) {
        String resolvedTenantId = requiredTenant(tenantId);
        String normalizedStatus = normalized(status);
        List<CurriculumSubject> subjects = loadSubjects(resolvedTenantId, normalizedStatus, subjectId);
        List<String> subjectIds = subjects.stream().map(CurriculumSubject::getId).toList();
        List<CurriculumLevel> levels = subjectIds.isEmpty()
                ? List.of()
                : loadLevels(resolvedTenantId, normalizedStatus, subjectIds, categoryId);
        List<String> levelIds = levels.stream().map(CurriculumLevel::getId).toList();
        List<CurriculumTopic> topics = levelIds.isEmpty()
                ? List.of()
                : loadTopics(resolvedTenantId, normalizedStatus, levelIds, firstNonBlank(topicId, sectionId));

        Map<String, CurriculumLevel> levelsById = levels.stream()
                .collect(Collectors.toMap(CurriculumLevel::getId, Function.identity()));
        Map<String, CurriculumTopic> topicsById = topics.stream()
                .collect(Collectors.toMap(CurriculumTopic::getId, Function.identity()));
        Map<String, Long> resourceCounts = resourceCountsByNode(resolvedTenantId, subjects, levels, topics);
        List<LearningResourceTaxonomyNodeResponse> taxonomyTree = taxonomyNodes(
                subjects,
                levels,
                topics,
                resourceCounts
        );
        PageResponse<LearningResourceCardResponse> resources = resources(
                resolvedTenantId,
                subjectId,
                categoryId,
                firstNonBlank(topicId, sectionId),
                normalizedStatus,
                normalized(visibility),
                safePage(page),
                safeSize(size),
                levelsById,
                topicsById
        );
        return new LearningResourceLibraryResponse(
                resolvedTenantId,
                blankToNull(educationUnitId),
                taxonomyTree,
                resources,
                List.<LearningResourceProgressResponse>of(),
                List.<LearningRecentResourceResponse>of()
        );
    }

    private List<CurriculumSubject> loadSubjects(String tenantId, String status, String subjectId) {
        Pageable limit = PageRequest.of(0, HARD_MAX_TAXONOMY_NODES_PER_LEVEL + 1);
        List<CurriculumSubject> subjects = status == null
                ? subjectRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId, limit)
                : subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(tenantId, status, limit);
        rejectOversizedTaxonomy("subjects", subjects);
        String filter = blankToNull(subjectId);
        if (filter == null) {
            return subjects;
        }
        return subjects.stream().filter(subject -> subject.getId().equals(filter)).toList();
    }

    private List<CurriculumLevel> loadLevels(
            String tenantId,
            String status,
            List<String> subjectIds,
            String levelId
    ) {
        Pageable limit = PageRequest.of(0, HARD_MAX_TAXONOMY_NODES_PER_LEVEL + 1);
        List<CurriculumLevel> levels = status == null
                ? levelRepository.findByTenantIdAndSubjectIdInOrderBySortOrderAscNameAsc(tenantId, subjectIds, limit)
                : levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                        tenantId,
                        subjectIds,
                        status,
                        limit
                );
        rejectOversizedTaxonomy("levels", levels);
        String filter = blankToNull(levelId);
        if (filter == null) {
            return levels;
        }
        return levels.stream().filter(level -> level.getId().equals(filter)).toList();
    }

    private List<CurriculumTopic> loadTopics(
            String tenantId,
            String status,
            List<String> levelIds,
            String topicId
    ) {
        Pageable limit = PageRequest.of(0, HARD_MAX_TAXONOMY_NODES_PER_LEVEL + 1);
        List<CurriculumTopic> topics = status == null
                ? topicRepository.findByTenantIdAndLevelIdInOrderBySortOrderAscNameAsc(tenantId, levelIds, limit)
                : topicRepository.findByTenantIdAndLevelIdInAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(
                        tenantId,
                        levelIds,
                        status,
                        limit
                );
        rejectOversizedTaxonomy("topics", topics);
        String filter = blankToNull(topicId);
        if (filter == null) {
            return topics;
        }
        return topics.stream().filter(topic -> topic.getId().equals(filter)).toList();
    }

    private void rejectOversizedTaxonomy(String nodeType, List<?> nodes) {
        if (nodes != null && nodes.size() > HARD_MAX_TAXONOMY_NODES_PER_LEVEL) {
            throw new BadRequestException(
                    "learningResourceLibrary taxonomy is too large; filter by subjectId, gradeId, or topicId"
            );
        }
    }

    private Map<String, Long> resourceCountsByNode(
            String tenantId,
            List<CurriculumSubject> subjects,
            List<CurriculumLevel> levels,
            List<CurriculumTopic> topics
    ) {
        Map<String, Long> counts = new HashMap<>();
        Map<String, CurriculumLevel> levelsById = levels.stream()
                .collect(Collectors.toMap(CurriculumLevel::getId, Function.identity()));
        List<String> topicIds = topics.stream().map(CurriculumTopic::getId).toList();
        Map<String, Long> topicCounts = topicIds.isEmpty()
                ? Map.of()
                : placementRepository.countByTenantIdAndTopicIdInGrouped(tenantId, topicIds)
                        .stream()
                        .collect(Collectors.toMap(
                                TopicContentCountProjection::getTopicId,
                                TopicContentCountProjection::getContentCount,
                                Long::sum,
                                LinkedHashMap::new
                        ));
        topicCounts.forEach(counts::put);
        for (CurriculumTopic topic : topics) {
            long count = topicCounts.getOrDefault(topic.getId(), 0L);
            CurriculumLevel level = levelsById.get(topic.getLevelId());
            if (level != null) {
                counts.merge(level.getId(), count, Long::sum);
                counts.merge(level.getSubjectId(), count, Long::sum);
            }
        }
        subjects.forEach(subject -> counts.putIfAbsent(subject.getId(), 0L));
        levels.forEach(level -> counts.putIfAbsent(level.getId(), 0L));
        return counts;
    }

    private List<LearningResourceTaxonomyNodeResponse> taxonomyNodes(
            List<CurriculumSubject> subjects,
            List<CurriculumLevel> levels,
            List<CurriculumTopic> topics,
            Map<String, Long> resourceCounts
    ) {
        Map<String, Long> levelChildCounts = topics.stream()
                .collect(Collectors.groupingBy(CurriculumTopic::getLevelId, Collectors.counting()));
        Map<String, Long> subjectChildCounts = levels.stream()
                .collect(Collectors.groupingBy(CurriculumLevel::getSubjectId, Collectors.counting()));
        List<LearningResourceTaxonomyNodeResponse> subjectNodes = subjects.stream()
                .map(subject -> new LearningResourceTaxonomyNodeResponse(
                        subject.getId(),
                        "subject",
                        subject.getName(),
                        subject.getCode(),
                        null,
                        subject.getId(),
                        null,
                        valueOrZero(subject.getSortOrder()),
                        subject.getStatus(),
                        subject.getDescription(),
                        subjectChildCounts.getOrDefault(subject.getId(), 0L),
                        resourceCounts.getOrDefault(subject.getId(), 0L)
                ))
                .toList();
        List<LearningResourceTaxonomyNodeResponse> levelNodes = levels.stream()
                .map(level -> new LearningResourceTaxonomyNodeResponse(
                        level.getId(),
                        "level",
                        level.getName(),
                        level.getCode(),
                        level.getSubjectId(),
                        level.getSubjectId(),
                        level.getId(),
                        valueOrZero(level.getSortOrder()),
                        level.getStatus(),
                        level.getDescription(),
                        levelChildCounts.getOrDefault(level.getId(), 0L),
                        resourceCounts.getOrDefault(level.getId(), 0L)
                ))
                .toList();
        List<LearningResourceTaxonomyNodeResponse> topicNodes = topics.stream()
                .map(topic -> new LearningResourceTaxonomyNodeResponse(
                        topic.getId(),
                        "topic",
                        topic.getName(),
                        topic.getCode(),
                        topic.getLevelId(),
                        null,
                        topic.getLevelId(),
                        valueOrZero(topic.getSortOrder()),
                        topic.getStatus(),
                        topic.getDescription(),
                        0,
                        resourceCounts.getOrDefault(topic.getId(), 0L)
                ))
                .toList();
        return java.util.stream.Stream.of(subjectNodes, levelNodes, topicNodes)
                .flatMap(List::stream)
                .toList();
    }

    private PageResponse<LearningResourceCardResponse> resources(
            String tenantId,
            String subjectId,
            String categoryId,
            String topicId,
            String status,
            String visibility,
            int page,
            int size,
            Map<String, CurriculumLevel> levelsById,
            Map<String, CurriculumTopic> topicsById
    ) {
        org.springframework.data.domain.Page<LearningResourceCardProjection> result =
                contentItemRepository.findLearningResourceCards(
                        tenantId,
                        blankToNull(subjectId),
                        blankToNull(categoryId),
                        blankToNull(topicId),
                        status,
                        visibility,
                        PageRequest.of(page, size)
                );
        List<LearningResourceCardResponse> cards = result.getContent().stream()
                .map(this::card)
                .toList();
        long total = result.getTotalElements();
        int totalPages = result.getTotalPages();
        return new PageResponse<>(
                cards,
                page,
                size,
                total,
                totalPages,
                page + 1 < totalPages,
                page > 0
        );
    }

    private LearningResourceCardResponse card(LearningResourceCardProjection card) {
        return new LearningResourceCardResponse(
                card.getId(), card.getSubjectId(), null, card.getCategoryId(), card.getTopicId(),
                card.getTopicId(), card.getTitle(), card.getId(), card.getSubtitle(), null,
                card.getVisibility(), card.getStatus(), null,
                card.getUpdatedAt() == null ? null : card.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC));
    }

    private LearningResourceCardResponse card(
            ContentItem item,
            TopicContentItem placement,
            Map<String, CurriculumLevel> levelsById,
            Map<String, CurriculumTopic> topicsById
    ) {
        String topicId = placement == null ? null : placement.getTopicId();
        CurriculumTopic topic = topicId == null ? null : topicsById.get(topicId);
        CurriculumLevel level = topic == null ? null : levelsById.get(topic.getLevelId());
        return new LearningResourceCardResponse(
                item.getId(),
                level == null ? null : level.getSubjectId(),
                null,
                level == null ? null : level.getId(),
                topicId,
                topicId,
                item.getTitle(),
                item.getId(),
                item.getDescription(),
                null,
                item.getVisibility(),
                item.getStatus(),
                null,
                item.getUpdatedAt()
        );
    }

    private String requiredTenant(String tenantId) {
        String normalized = blankToNull(tenantId);
        return normalized == null ? "erg" : normalized;
    }

    private String normalized(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private boolean contentStatusMatches(String requestedStatus, String itemStatus) {
        if (requestedStatus == null) {
            return true;
        }
        String normalizedItemStatus = normalized(itemStatus);
        if (ACTIVE.equals(requestedStatus)) {
            return ACTIVE.equals(normalizedItemStatus) || "published".equals(normalizedItemStatus);
        }
        return requestedStatus.equals(normalizedItemStatus);
    }

    private String firstNonBlank(String first, String second) {
        String normalized = blankToNull(first);
        return normalized == null ? blankToNull(second) : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int safePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int safeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, HARD_MAX_PAGE_SIZE);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
