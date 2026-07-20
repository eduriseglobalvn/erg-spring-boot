package com.erg.ergspringboot.service.learningcontent;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.learningcontent.ContentAsset;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.ContentItemAsset;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import com.erg.ergspringboot.repository.learningcontent.ContentAssetRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemAssetRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository;
import com.erg.ergspringboot.repository.learningcontent.CurriculumTopicLookupRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.ContentAssetResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.ContentItemListResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.ContentItemResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.GoogleSlidesLectureResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.TopicContentPlacementResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.AttachContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateGoogleSlidesLectureRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.PlaceContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.ReorderTopicContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.UpdateContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.UpdateContentItemRequest;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.net.URI;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager")
public class LearningContentService {

    private static final String DEFAULT_STATUS = "draft";
    private static final String DEFAULT_ASSET_STATUS = "active";
    private static final String DEFAULT_VISIBILITY = "private";
    private static final String DEFAULT_PLACEMENT_STATUS = "active";
    private static final String DEFAULT_ASSET_ROLE = "primary";

    private final ContentItemRepository contentItemRepository;
    private final ContentAssetRepository contentAssetRepository;
    private final ContentItemAssetRepository contentItemAssetRepository;
    private final TopicContentItemRepository topicContentItemRepository;
    private final CurriculumTopicLookupRepository curriculumTopicLookupRepository;

    public LearningContentService(
            ContentItemRepository contentItemRepository,
            ContentAssetRepository contentAssetRepository,
            ContentItemAssetRepository contentItemAssetRepository,
            TopicContentItemRepository topicContentItemRepository,
            CurriculumTopicLookupRepository curriculumTopicLookupRepository
    ) {
        this.contentItemRepository = contentItemRepository;
        this.contentAssetRepository = contentAssetRepository;
        this.contentItemAssetRepository = contentItemAssetRepository;
        this.topicContentItemRepository = topicContentItemRepository;
        this.curriculumTopicLookupRepository = curriculumTopicLookupRepository;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public ContentItemListResponse listContentItems(String tenantId, String topicId, Integer limit) {
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

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public ContentItem createContentItem(String tenantId, String userId, CreateContentItemRequest request) {
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

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public GoogleSlidesLectureResponse createGoogleSlidesLecture(
            String tenantId,
            String userId,
            CreateGoogleSlidesLectureRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("lecture request is required");
        }
        String topicId = required(request.topicId(), "topicId");
        String slidesUrl = requiredGoogleSlidesUrl(request.googleSlidesUrl());

        ContentItem item = createContentItem(tenantId, userId, new CreateContentItemRequest(
                "LECTURE",
                request.title(),
                null,
                normalizedOrDefault(request.status(), "published"),
                normalizedOrDefault(request.visibility(), "public")
        ));
        ContentAsset asset = createContentAsset(tenantId, userId, new CreateContentAssetRequest(
                "GOOGLE_SLIDE",
                request.title(),
                "google_slides",
                null,
                slidesUrl,
                "application/vnd.google-apps.presentation",
                null,
                null,
                "active",
                Map.of("launchMode", "embed", "canDownload", false)
        ));
        ContentItemAsset assetLink = attachContentAsset(tenantId, item.getId(), userId, new AttachContentAssetRequest(
                asset.getId(),
                "primary",
                1,
                Map.of("source", "google_slides")
        ));
        TopicContentItem placement = placeContentItem(tenantId, topicId, userId, new PlaceContentItemRequest(
                item.getId(),
                "LECTURE",
                null,
                "active"
        ));
        return new GoogleSlidesLectureResponse(item, asset, assetLink, placement);
    }

    private String requiredGoogleSlidesUrl(String value) {
        String url = required(value, "googleSlidesUrl");
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || !host.equalsIgnoreCase("docs.google.com")
                    || !uri.getPath().matches("^/presentation/d/[^/]+/(embed|edit|present|pub)/?$")) {
                throw new BadRequestException("googleSlidesUrl must be a Google Slides presentation URL");
            }
            return url;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("googleSlidesUrl must be a valid URL");
        }
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public ContentItem updateContentItem(
            String tenantId,
            String contentItemId,
            String userId,
            UpdateContentItemRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("content item update request is required");
        }
        String resolvedTenantId = required(tenantId, "tenantId");
        ContentItem item = contentItemRepository.findByIdAndTenantId(
                        required(contentItemId, "contentItemId"),
                        resolvedTenantId
                )
                .orElseThrow(() -> new ResourceNotFoundException("content item not found for tenant"));
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
        return contentItemRepository.save(item);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public void deleteContentItem(String tenantId, String contentItemId) {
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

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public ContentAsset createContentAsset(String tenantId, String userId, CreateContentAssetRequest request) {
        if (request == null) {
            throw new BadRequestException("content asset request is required");
        }
        ContentAsset asset = new ContentAsset();
        asset.setTenantId(required(tenantId, "tenantId"));
        asset.setAssetType(requiredUpper(request.assetType(), "assetType"));
        asset.setTitle(blankToNull(request.title()));
        asset.setStorageProvider(blankToNull(request.storageProvider()));
        asset.setStorageKey(blankToNull(request.storageKey()));
        asset.setStorageUrl(blankToNull(request.storageUrl()));
        asset.setMimeType(blankToNull(request.mimeType()));
        asset.setByteSize(nonNegative(request.byteSize(), "byteSize"));
        asset.setChecksumSha256(blankToNull(request.checksumSha256()));
        asset.setStatus(normalizedOrDefault(request.status(), DEFAULT_ASSET_STATUS));
        asset.setMetadata(copyMetadata(request.metadata()));
        asset.setCreatedBy(blankToNull(userId));
        asset.setUpdatedBy(blankToNull(userId));
        return contentAssetRepository.save(asset);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public ContentAsset updateContentAsset(
            String tenantId,
            String contentAssetId,
            String userId,
            UpdateContentAssetRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("content asset update request is required");
        }
        ContentAsset asset = contentAssetRepository.findByIdAndTenantId(
                        required(contentAssetId, "contentAssetId"),
                        required(tenantId, "tenantId")
                )
                .orElseThrow(() -> new ResourceNotFoundException("content asset not found for tenant"));
        if (request.assetType() != null) {
            asset.setAssetType(requiredUpper(request.assetType(), "assetType"));
        }
        if (request.title() != null) {
            asset.setTitle(blankToNull(request.title()));
        }
        if (request.storageProvider() != null) {
            asset.setStorageProvider(blankToNull(request.storageProvider()));
        }
        if (request.storageKey() != null) {
            asset.setStorageKey(blankToNull(request.storageKey()));
        }
        if (request.storageUrl() != null) {
            asset.setStorageUrl(blankToNull(request.storageUrl()));
        }
        if (request.mimeType() != null) {
            asset.setMimeType(blankToNull(request.mimeType()));
        }
        if (request.byteSize() != null) {
            asset.setByteSize(nonNegative(request.byteSize(), "byteSize"));
        }
        if (request.checksumSha256() != null) {
            asset.setChecksumSha256(blankToNull(request.checksumSha256()));
        }
        if (request.status() != null) {
            asset.setStatus(normalizedOrDefault(request.status(), DEFAULT_ASSET_STATUS));
        }
        if (request.metadata() != null) {
            Map<String, Object> mergedMetadata = copyMetadata(asset.getMetadata());
            mergedMetadata.putAll(copyMetadata(request.metadata()));
            asset.setMetadata(mergedMetadata);
        }
        asset.setUpdatedBy(blankToNull(userId));
        return contentAssetRepository.save(asset);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public ContentItemAsset attachContentAsset(
            String tenantId,
            String contentItemId,
            String userId,
            AttachContentAssetRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("content asset attachment request is required");
        }
        String resolvedTenantId = required(tenantId, "tenantId");
        String resolvedContentItemId = required(contentItemId, "contentItemId");
        ContentItem item = contentItemRepository.findByIdAndTenantId(resolvedContentItemId, resolvedTenantId)
                .orElseThrow(() -> new ResourceNotFoundException("content item not found for tenant"));
        ContentAsset asset = contentAssetRepository.findByIdAndTenantId(
                        required(request.contentAssetId(), "contentAssetId"),
                        resolvedTenantId
                )
                .orElseThrow(() -> new ResourceNotFoundException("content asset not found for tenant"));

        ContentItemAsset link = new ContentItemAsset();
        link.setTenantId(resolvedTenantId);
        link.setContentItemId(item.getId());
        link.setContentAssetId(asset.getId());
        link.setAssetRole(normalizedOrDefault(request.assetRole(), DEFAULT_ASSET_ROLE));
        link.setSortOrder(request.sortOrder() == null
                ? contentItemAssetRepository.nextSortOrder(resolvedTenantId, resolvedContentItemId)
                : positive(request.sortOrder(), "sortOrder"));
        link.setMetadata(copyMetadata(request.metadata()));
        link.setCreatedBy(blankToNull(userId));
        return contentItemAssetRepository.save(link);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public TopicContentItem placeContentItem(
            String tenantId,
            String topicId,
            String userId,
            PlaceContentItemRequest request
    ) {
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

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public void removePlacement(String tenantId, String topicId, String placementId) {
        TopicContentItem placement = topicContentItemRepository.findByIdAndTenantIdAndTopicId(
                        required(placementId, "placementId"),
                        required(tenantId, "tenantId"),
                        required(topicId, "topicId")
                )
                .orElseThrow(() -> new ResourceNotFoundException("topic content placement not found"));
        topicContentItemRepository.delete(placement);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public List<TopicContentItem> reorderPlacements(
            String tenantId,
            String topicId,
            List<ReorderTopicContentItemRequest> requests
    ) {
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

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private String requiredUpper(String value, String fieldName) {
        return required(value, fieldName).toUpperCase(Locale.ROOT);
    }

    private String normalizedOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        return metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    private Integer positive(Integer value, String fieldName) {
        if (value == null || value < 1) {
            throw new BadRequestException(fieldName + " must be positive");
        }
        return value;
    }

    private Long nonNegative(Long value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw new BadRequestException(fieldName + " must be non-negative");
        }
        return value;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 100;
        }
        if (limit < 1) {
            throw new BadRequestException("limit must be positive");
        }
        return Math.min(limit, 500);
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
