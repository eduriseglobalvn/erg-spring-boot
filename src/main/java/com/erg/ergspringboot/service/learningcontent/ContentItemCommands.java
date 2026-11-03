package com.erg.ergspringboot.service.learningcontent;

import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.blankToNull;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.copyMetadata;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.normalizeLimit;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.normalizedOrDefault;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.required;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.requiredUpper;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.learningcontent.ContentAsset;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.ContentItemAsset;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import com.erg.ergspringboot.platform.sync.LmsSyncOperationService;
import com.erg.ergspringboot.repository.learningcontent.ContentAssetRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemAssetRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.ContentAssetResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.ContentItemListResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.ContentItemResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.TopicContentPlacementResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.UpdateContentItemRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;

/**
 * Content-item reads and writes (list with assets/placements, create, versioned update, cascading delete).
 * A plain collaborator of {@link LearningContentService}: transactions and cache eviction stay on the facade.
 */
final class ContentItemCommands {

    private static final String DEFAULT_STATUS = "draft";
    private static final String DEFAULT_VISIBILITY = "private";

    private final ContentItemRepository contentItemRepository;
    private final ContentAssetRepository contentAssetRepository;
    private final ContentItemAssetRepository contentItemAssetRepository;
    private final TopicContentItemRepository topicContentItemRepository;
    private final LearningContentSupport.SyncReceipts receipts;

    ContentItemCommands(
            ContentItemRepository contentItemRepository,
            ContentAssetRepository contentAssetRepository,
            ContentItemAssetRepository contentItemAssetRepository,
            TopicContentItemRepository topicContentItemRepository,
            LearningContentSupport.SyncReceipts receipts
    ) {
        this.contentItemRepository = contentItemRepository;
        this.contentAssetRepository = contentAssetRepository;
        this.contentItemAssetRepository = contentItemAssetRepository;
        this.topicContentItemRepository = topicContentItemRepository;
        this.receipts = receipts;
    }

    ContentItemListResponse list(String tenantId, String topicId, Integer limit) {
        String resolvedTenantId = required(tenantId, "tenantId");
        int pageSize = normalizeLimit(limit);
        List<ContentItem> items;
        long total;

        if (topicId == null || topicId.isBlank()) {
            items = contentItemRepository.findAllByTenantIdOrderByUpdatedAtDesc(
                    resolvedTenantId,
                    PageRequest.of(0, pageSize)
            );
            total = contentItemRepository.countByTenantId(resolvedTenantId);
        } else {
            String resolvedTopicId = topicId.trim();
            List<TopicContentItem> placements = topicContentItemRepository
                    .findAllByTenantIdAndTopicIdOrderBySortOrderAsc(
                            resolvedTenantId, resolvedTopicId, PageRequest.of(0, pageSize));
            total = topicContentItemRepository.countByTenantIdAndTopicId(resolvedTenantId, resolvedTopicId);
            List<String> contentItemIds = placements.stream()
                    .map(TopicContentItem::getContentItemId)
                    .distinct()
                    .toList();
            Map<String, ContentItem> byId = contentItemRepository
                    .findAllByTenantIdAndIdIn(resolvedTenantId, contentItemIds)
                    .stream()
                    .collect(Collectors.toMap(ContentItem::getId, Function.identity()));
            items = contentItemIds.stream()
                    .map(byId::get)
                    .filter(item -> item != null)
                    .toList();
        }

        if (items.isEmpty()) {
            return new ContentItemListResponse(List.of(), total);
        }

        List<String> contentItemIds = items.stream().map(ContentItem::getId).toList();
        Map<String, List<TopicContentItem>> placementsByItem = topicContentItemRepository
                .findAllByTenantIdAndContentItemIdIn(resolvedTenantId, contentItemIds)
                .stream()
                .collect(Collectors.groupingBy(TopicContentItem::getContentItemId));
        Map<String, List<ContentItemAsset>> linksByItem = contentItemAssetRepository
                .findAllByTenantIdAndContentItemIdIn(resolvedTenantId, contentItemIds)
                .stream()
                .collect(Collectors.groupingBy(ContentItemAsset::getContentItemId));
        List<String> assetIds = linksByItem.values().stream()
                .flatMap(List::stream)
                .map(ContentItemAsset::getContentAssetId)
                .distinct()
                .toList();
        Map<String, ContentAsset> assetsById = assetIds.isEmpty()
                ? Map.of()
                : contentAssetRepository.findAllByTenantIdAndIdIn(resolvedTenantId, assetIds)
                        .stream()
                        .collect(Collectors.toMap(ContentAsset::getId, Function.identity()));

        List<ContentItemResponse> responses = items.stream()
                .map(item -> toContentItemResponse(
                        item,
                        linksByItem.getOrDefault(item.getId(), List.of()),
                        assetsById,
                        placementsByItem.getOrDefault(item.getId(), List.of())
                ))
                .toList();
        return new ContentItemListResponse(responses, total);
    }

    ContentItem create(String tenantId, String userId, CreateContentItemRequest request) {
        if (request == null) {
            throw new BadRequestException("content item request is required");
        }
        ContentItem item = new ContentItem();
        item.setTenantId(required(tenantId, "tenantId"));
        item.setContentType(requiredUpper(request.contentType(), "contentType"));
        item.setTitle(required(request.title(), "title"));
        item.setDescription(blankToNull(request.description()));
        item.setStatus(normalizedOrDefault(request.status(), DEFAULT_STATUS));
        item.setVisibility(normalizedOrDefault(request.visibility(), DEFAULT_VISIBILITY));
        item.setOwnerId(blankToNull(userId));
        item.setCreatedBy(blankToNull(userId));
        item.setUpdatedBy(blankToNull(userId));
        return contentItemRepository.save(item);
    }

    ContentItem update(String tenantId, String contentItemId, String userId, UpdateContentItemRequest request) {
        if (request == null) {
            throw new BadRequestException("content item update request is required");
        }
        String resolvedTenantId = required(tenantId, "tenantId");
        ContentItem item = contentItemRepository.findByIdAndTenantId(
                        required(contentItemId, "contentItemId"),
                        resolvedTenantId
                )
                .orElseThrow(() -> new ResourceNotFoundException("content item not found for tenant"));
        String key = blankToNull(request.idempotencyKey());
        LmsSyncOperationService.Receipt receipt = receipts.reserve(resolvedTenantId, userId, request.operationId(), key,
                "lms.content_item", item.getId(), request, request.expectedVersion());
        if (receipt != null && receipt.replayed()) return receipts.result(receipt, ContentItem.class);
        try {
            receipts.checkVersion(request.operationId(), key, "lms.content_item", item.getId(),
                    request.expectedVersion(), version(item));
            if (request.contentType() != null) {
                item.setContentType(requiredUpper(request.contentType(), "contentType"));
            }
            if (request.title() != null) {
                item.setTitle(required(request.title(), "title"));
            }
            if (request.description() != null) {
                item.setDescription(blankToNull(request.description()));
            }
            if (request.status() != null) {
                item.setStatus(normalizedOrDefault(request.status(), DEFAULT_STATUS));
            }
            if (request.visibility() != null) {
                item.setVisibility(normalizedOrDefault(request.visibility(), DEFAULT_VISIBILITY));
            }
            item.setUpdatedBy(blankToNull(userId));
            ContentItem saved = contentItemRepository.save(item);
            receipts.complete(receipt, saved, version(saved));
            return saved;
        } catch (RuntimeException exception) {
            receipts.abandon(receipt);
            throw exception;
        }
    }

    void delete(String tenantId, String contentItemId) {
        String resolvedTenantId = required(tenantId, "tenantId");
        ContentItem item = contentItemRepository.findByIdAndTenantId(
                        required(contentItemId, "contentItemId"),
                        resolvedTenantId
                )
                .orElseThrow(() -> new ResourceNotFoundException("content item not found for tenant"));
        List<ContentItemAsset> assetLinks = contentItemAssetRepository.findAllByTenantIdAndContentItemId(
                resolvedTenantId,
                item.getId()
        );
        List<TopicContentItem> placements = topicContentItemRepository.findAllByTenantIdAndContentItemIdIn(
                resolvedTenantId,
                List.of(item.getId())
        );
        topicContentItemRepository.deleteAll(placements);
        contentItemAssetRepository.deleteAll(assetLinks);
        contentItemRepository.delete(item);
    }

    private long version(ContentItem item) {
        return item.getRecordVersion() == null ? 0L : item.getRecordVersion();
    }

    private ContentItemResponse toContentItemResponse(
            ContentItem item,
            List<ContentItemAsset> links,
            Map<String, ContentAsset> assetsById,
            List<TopicContentItem> placements
    ) {
        return new ContentItemResponse(
                item.getId(),
                item.getContentType(),
                item.getTitle(),
                item.getDescription(),
                item.getStatus(),
                item.getVisibility(),
                item.getOwnerId(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                links.stream()
                        .sorted(Comparator.comparing(ContentItemAsset::getSortOrder))
                        .map(link -> toContentAssetResponse(link, assetsById.get(link.getContentAssetId())))
                        .filter(asset -> asset != null)
                        .toList(),
                placements.stream()
                        .sorted(Comparator.comparing(TopicContentItem::getSortOrder))
                        .map(this::toPlacementResponse)
                        .toList()
        );
    }

    private ContentAssetResponse toContentAssetResponse(ContentItemAsset link, ContentAsset asset) {
        if (asset == null) {
            return null;
        }
        return new ContentAssetResponse(
                asset.getId(),
                asset.getAssetType(),
                asset.getTitle(),
                asset.getStorageProvider(),
                asset.getStorageKey(),
                asset.getStorageUrl(),
                asset.getMimeType(),
                asset.getByteSize(),
                asset.getChecksumSha256(),
                asset.getStatus(),
                copyMetadata(asset.getMetadata()),
                link.getAssetRole(),
                link.getSortOrder()
        );
    }

    private TopicContentPlacementResponse toPlacementResponse(TopicContentItem placement) {
        return new TopicContentPlacementResponse(
                placement.getId(),
                placement.getTopicId(),
                placement.getContentRole(),
                placement.getSortOrder(),
                placement.getStatus()
        );
    }
}
