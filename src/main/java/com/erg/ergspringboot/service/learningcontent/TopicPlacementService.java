package com.erg.ergspringboot.service.learningcontent;

import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.blankToNull;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.normalizedOrDefault;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.positive;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.required;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.requiredUpper;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository;
import com.erg.ergspringboot.repository.learningcontent.CurriculumTopicLookupRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.PlaceContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.ReorderTopicContentItemRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Placement of content items on curriculum topics (place, remove, reorder). A plain collaborator of
 * {@link LearningContentService}, not a Spring bean: transactions and cache eviction stay on the facade.
 */
final class TopicPlacementService {

    private static final String DEFAULT_PLACEMENT_STATUS = "active";

    private final ContentItemRepository contentItemRepository;
    private final TopicContentItemRepository topicContentItemRepository;
    private final CurriculumTopicLookupRepository curriculumTopicLookupRepository;

    TopicPlacementService(
            ContentItemRepository contentItemRepository,
            TopicContentItemRepository topicContentItemRepository,
            CurriculumTopicLookupRepository curriculumTopicLookupRepository
    ) {
        this.contentItemRepository = contentItemRepository;
        this.topicContentItemRepository = topicContentItemRepository;
        this.curriculumTopicLookupRepository = curriculumTopicLookupRepository;
    }

    TopicContentItem place(String tenantId, String topicId, String userId, PlaceContentItemRequest request) {
        if (request == null) {
            throw new BadRequestException("placement request is required");
        }
        String resolvedTenantId = required(tenantId, "tenantId");
        String resolvedTopicId = required(topicId, "topicId");
        if (!curriculumTopicLookupRepository.existsActiveTopicInTenant(resolvedTopicId, resolvedTenantId)) {
            throw new ResourceNotFoundException("curriculum topic not found for tenant");
        }
        ContentItem contentItem = contentItemRepository.findByIdAndTenantId(
                        required(request.contentItemId(), "contentItemId"),
                        resolvedTenantId
                )
                .orElseThrow(() -> new ResourceNotFoundException("content item not found for tenant"));

        TopicContentItem placement = new TopicContentItem();
        placement.setTenantId(resolvedTenantId);
        placement.setTopicId(resolvedTopicId);
        placement.setContentItemId(contentItem.getId());
        placement.setContentRole(requiredUpper(request.contentRole(), "contentRole"));
        placement.setSortOrder(request.sortOrder() == null
                ? topicContentItemRepository.nextSortOrder(resolvedTenantId, resolvedTopicId)
                : positive(request.sortOrder(), "sortOrder"));
        placement.setStatus(normalizedOrDefault(request.status(), DEFAULT_PLACEMENT_STATUS));
        placement.setCreatedBy(blankToNull(userId));
        return topicContentItemRepository.save(placement);
    }

    void remove(String tenantId, String topicId, String placementId) {
        TopicContentItem placement = topicContentItemRepository.findByIdAndTenantIdAndTopicId(
                        required(placementId, "placementId"),
                        required(tenantId, "tenantId"),
                        required(topicId, "topicId")
                )
                .orElseThrow(() -> new ResourceNotFoundException("topic content placement not found"));
        topicContentItemRepository.delete(placement);
    }

    List<TopicContentItem> reorder(String tenantId, String topicId, List<ReorderTopicContentItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("placements are required");
        }
        String resolvedTenantId = required(tenantId, "tenantId");
        String resolvedTopicId = required(topicId, "topicId");
        List<String> placementIds = requests.stream()
                .map(request -> required(request == null ? null : request.placementId(), "placementId"))
                .distinct()
                .toList();
        if (placementIds.size() != requests.size()) {
            throw new BadRequestException("placement ids must be unique");
        }

        List<TopicContentItem> placements = topicContentItemRepository.findAllByTenantIdAndTopicIdAndIdIn(
                resolvedTenantId,
                resolvedTopicId,
                placementIds
        );
        if (placements.size() != placementIds.size()) {
            throw new BadRequestException("all placements must belong to the same topic");
        }

        Map<String, TopicContentItem> byId = new HashMap<>();
        for (TopicContentItem placement : placements) {
            byId.put(placement.getId(), placement);
        }
        for (ReorderTopicContentItemRequest request : requests) {
            byId.get(request.placementId()).setSortOrder(positive(request.sortOrder(), "sortOrder"));
        }
        return topicContentItemRepository.saveAll(placements);
    }
}
