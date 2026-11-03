package com.erg.ergspringboot.service.learningcontent;

import com.erg.ergspringboot.model.learningcontent.ContentAsset;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.ContentItemAsset;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import com.erg.ergspringboot.repository.learningcontent.ContentAssetRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemAssetRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository;
import com.erg.ergspringboot.repository.learningcontent.CurriculumTopicLookupRepository;
import com.erg.ergspringboot.repository.learningcontent.TopicContentItemRepository;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.ContentItemListResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.GoogleSlidesLectureResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.AttachContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateGoogleSlidesLectureRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.PlaceContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.ReorderTopicContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.UpdateContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.UpdateContentItemRequest;
import com.erg.ergspringboot.platform.sync.LmsSyncOperationService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Legacy persistence adapter retained during the strangler migration. New
 * callers must use {@code learningcontent.LearningContentOperations}; this
 * service is not a public module contract.
 *
 * <p>Thin transactional facade (SQ-20): it owns the transaction and cache-eviction boundary and delegates
 * to plain, non-proxied collaborators ({@link ContentItemCommands}, {@link ContentAssetCommands},
 * {@link TopicPlacementService}, {@link GoogleSlidesLectureFactory}).
 */
@Deprecated(since = "2026-08-08", forRemoval = false)
@Service
@Transactional(value = "postgresTransactionManager")
public class LearningContentService {

    private final ContentItemCommands contentItems;
    private final ContentAssetCommands contentAssets;
    private final TopicPlacementService topicPlacements;
    private final GoogleSlidesLectureFactory googleSlidesLectures;

    public LearningContentService(
            ContentItemRepository contentItemRepository,
            ContentAssetRepository contentAssetRepository,
            ContentItemAssetRepository contentItemAssetRepository,
            TopicContentItemRepository topicContentItemRepository,
            CurriculumTopicLookupRepository curriculumTopicLookupRepository
    ) {
        this(contentItemRepository, contentAssetRepository, contentItemAssetRepository, topicContentItemRepository,
                curriculumTopicLookupRepository, null);
    }

    @Autowired
    public LearningContentService(
            ContentItemRepository contentItemRepository,
            ContentAssetRepository contentAssetRepository,
            ContentItemAssetRepository contentItemAssetRepository,
            TopicContentItemRepository topicContentItemRepository,
            CurriculumTopicLookupRepository curriculumTopicLookupRepository,
            ObjectProvider<LmsSyncOperationService> sync
    ) {
        LearningContentSupport.SyncReceipts receipts =
                new LearningContentSupport.SyncReceipts(sync == null ? null : sync.getIfAvailable());
        this.contentItems = new ContentItemCommands(contentItemRepository, contentAssetRepository,
                contentItemAssetRepository, topicContentItemRepository, receipts);
        this.contentAssets = new ContentAssetCommands(contentItemRepository, contentAssetRepository,
                contentItemAssetRepository, receipts);
        this.topicPlacements = new TopicPlacementService(contentItemRepository, topicContentItemRepository,
                curriculumTopicLookupRepository);
        this.googleSlidesLectures = new GoogleSlidesLectureFactory(contentItems, contentAssets, topicPlacements);
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public ContentItemListResponse listContentItems(String tenantId, String topicId, Integer limit) {
        return contentItems.list(tenantId, topicId, limit);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public ContentItem createContentItem(String tenantId, String userId, CreateContentItemRequest request) {
        return contentItems.create(tenantId, userId, request);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public GoogleSlidesLectureResponse createGoogleSlidesLecture(
            String tenantId,
            String userId,
            CreateGoogleSlidesLectureRequest request
    ) {
        return googleSlidesLectures.create(tenantId, userId, request);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public ContentItem updateContentItem(
            String tenantId,
            String contentItemId,
            String userId,
            UpdateContentItemRequest request
    ) {
        return contentItems.update(tenantId, contentItemId, userId, request);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public void deleteContentItem(String tenantId, String contentItemId) {
        contentItems.delete(tenantId, contentItemId);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public ContentAsset createContentAsset(String tenantId, String userId, CreateContentAssetRequest request) {
        return contentAssets.create(tenantId, userId, request);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public ContentAsset updateContentAsset(
            String tenantId,
            String contentAssetId,
            String userId,
            UpdateContentAssetRequest request
    ) {
        return contentAssets.update(tenantId, contentAssetId, userId, request);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public ContentItemAsset attachContentAsset(
            String tenantId,
            String contentItemId,
            String userId,
            AttachContentAssetRequest request
    ) {
        return contentAssets.attach(tenantId, contentItemId, userId, request);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public TopicContentItem placeContentItem(
            String tenantId,
            String topicId,
            String userId,
            PlaceContentItemRequest request
    ) {
        return topicPlacements.place(tenantId, topicId, userId, request);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public void removePlacement(String tenantId, String topicId, String placementId) {
        topicPlacements.remove(tenantId, topicId, placementId);
    }

    @CacheEvict(cacheNames = "learningResourceLibrary", allEntries = true)
    public List<TopicContentItem> reorderPlacements(
            String tenantId,
            String topicId,
            List<ReorderTopicContentItemRequest> requests
    ) {
        return topicPlacements.reorder(tenantId, topicId, requests);
    }
}
