package com.erg.ergspringboot.learningcontent.internal;

import com.erg.ergspringboot.learningcontent.LearningContentPort;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.ContentAssetResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.ContentItemAssetResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.ContentItemResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.DeletedResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.GoogleSlidesLectureResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.TopicContentItemResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.AttachContentAssetRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.CreateContentAssetRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.CreateContentItemRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.CreateGoogleSlidesLectureRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.PlaceContentItemRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.ReorderTopicContentItemRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.UpdateContentAssetRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.UpdateContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentService;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Compatibility adapter while the persistence implementation is strangled inward. */
@Primary
@Service
public class LegacyLearningContentAdapter implements LearningContentPort {

    private final LearningContentService legacy;

    public LegacyLearningContentAdapter(LearningContentService legacy) {
        this.legacy = legacy;
    }

    @Override
    public GoogleSlidesLectureResponse createGoogleSlidesLecture(
            String tenantId, String userId, CreateGoogleSlidesLectureRequest request) {
        return LearningContentAssembler.googleSlidesLecture(legacy.createGoogleSlidesLecture(tenantId, userId,
                new com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateGoogleSlidesLectureRequest(
                        request.topicId(), request.title(), request.googleSlidesUrl(), request.status(), request.visibility())));
    }

    @Override
    public ContentItemResponse createContentItem(
            String tenantId, String userId, CreateContentItemRequest request) {
        return LearningContentAssembler.contentItem(legacy.createContentItem(tenantId, userId,
                new com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentItemRequest(
                        request.contentType(), request.title(), request.description(), request.status(), request.visibility())));
    }

    @Override
    public ContentItemResponse updateContentItem(
            String tenantId, String contentItemId, String userId, UpdateContentItemRequest request) {
        return LearningContentAssembler.contentItem(
                legacy.updateContentItem(tenantId, contentItemId, userId,
                        new com.erg.ergspringboot.service.learningcontent.LearningContentRequests.UpdateContentItemRequest(
                                request.contentType(), request.title(), request.description(), request.status(), request.visibility(),
                                request.expectedVersion(), request.operationId(), request.idempotencyKey())));
    }

    @Override
    public DeletedResponse deleteContentItem(String tenantId, String contentItemId) {
        legacy.deleteContentItem(tenantId, contentItemId);
        return new DeletedResponse(true);
    }

    @Override
    public ContentAssetResponse createContentAsset(
            String tenantId, String userId, CreateContentAssetRequest request) {
        return LearningContentAssembler.contentAsset(legacy.createContentAsset(tenantId, userId,
                new com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentAssetRequest(
                        request.assetType(), request.title(), request.storageProvider(), request.storageKey(), request.storageUrl(),
                        request.mimeType(), request.byteSize(), request.checksumSha256(), request.status(), request.metadata())));
    }

    @Override
    public ContentAssetResponse updateContentAsset(
            String tenantId, String contentAssetId, String userId, UpdateContentAssetRequest request) {
        return LearningContentAssembler.contentAsset(
                legacy.updateContentAsset(tenantId, contentAssetId, userId,
                        new com.erg.ergspringboot.service.learningcontent.LearningContentRequests.UpdateContentAssetRequest(
                                request.assetType(), request.title(), request.storageProvider(), request.storageKey(), request.storageUrl(),
                                request.mimeType(), request.byteSize(), request.checksumSha256(), request.status(), request.metadata(),
                                request.expectedVersion(), request.operationId(), request.idempotencyKey())));
    }

    @Override
    public ContentItemAssetResponse attachContentAsset(
            String tenantId, String contentItemId, String userId, AttachContentAssetRequest request) {
        return LearningContentAssembler.contentItemAsset(
                legacy.attachContentAsset(tenantId, contentItemId, userId,
                        new com.erg.ergspringboot.service.learningcontent.LearningContentRequests.AttachContentAssetRequest(
                                request.contentAssetId(), request.assetRole(), request.sortOrder(), request.metadata())));
    }

    @Override
    public TopicContentItemResponse placeContentItem(
            String tenantId, String topicId, String userId, PlaceContentItemRequest request) {
        return LearningContentAssembler.topicContentItem(
                legacy.placeContentItem(tenantId, topicId, userId,
                        new com.erg.ergspringboot.service.learningcontent.LearningContentRequests.PlaceContentItemRequest(
                                request.contentItemId(), request.contentRole(), request.sortOrder(), request.status())));
    }

    @Override
    public DeletedResponse removePlacement(String tenantId, String topicId, String placementId) {
        legacy.removePlacement(tenantId, topicId, placementId);
        return new DeletedResponse(true);
    }

    @Override
    public List<TopicContentItemResponse> reorderPlacements(
            String tenantId, String topicId, List<ReorderTopicContentItemRequest> requests) {
        return legacy.reorderPlacements(tenantId, topicId, requests.stream()
                        .map(request -> new com.erg.ergspringboot.service.learningcontent.LearningContentRequests.ReorderTopicContentItemRequest(
                                request.placementId(), request.sortOrder()))
                        .toList()).stream()
                .map(LearningContentAssembler::topicContentItem)
                .toList();
    }
}
