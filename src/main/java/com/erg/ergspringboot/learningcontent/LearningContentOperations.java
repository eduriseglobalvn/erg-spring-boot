package com.erg.ergspringboot.learningcontent;

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
import java.util.List;
import org.springframework.stereotype.Service;

/** Public learning-content facade. Persistence details remain behind {@link LearningContentPort}. */
@Service
public class LearningContentOperations {

    private final LearningContentPort operations;

    public LearningContentOperations(LearningContentPort operations) {
        this.operations = operations;
    }

    public GoogleSlidesLectureResponse createGoogleSlidesLecture(
            String tenantId, String userId, CreateGoogleSlidesLectureRequest request) {
        return operations.createGoogleSlidesLecture(tenantId, userId, request);
    }

    public ContentItemResponse createContentItem(
            String tenantId, String userId, CreateContentItemRequest request) {
        return operations.createContentItem(tenantId, userId, request);
    }

    public ContentItemResponse updateContentItem(
            String tenantId, String contentItemId, String userId, UpdateContentItemRequest request) {
        return operations.updateContentItem(tenantId, contentItemId, userId, request);
    }

    public DeletedResponse deleteContentItem(String tenantId, String contentItemId) {
        return operations.deleteContentItem(tenantId, contentItemId);
    }

    public ContentAssetResponse createContentAsset(
            String tenantId, String userId, CreateContentAssetRequest request) {
        return operations.createContentAsset(tenantId, userId, request);
    }

    public ContentAssetResponse updateContentAsset(
            String tenantId, String contentAssetId, String userId, UpdateContentAssetRequest request) {
        return operations.updateContentAsset(tenantId, contentAssetId, userId, request);
    }

    public ContentItemAssetResponse attachContentAsset(
            String tenantId, String contentItemId, String userId, AttachContentAssetRequest request) {
        return operations.attachContentAsset(tenantId, contentItemId, userId, request);
    }

    public TopicContentItemResponse placeContentItem(
            String tenantId, String topicId, String userId, PlaceContentItemRequest request) {
        return operations.placeContentItem(tenantId, topicId, userId, request);
    }

    public DeletedResponse removePlacement(String tenantId, String topicId, String placementId) {
        return operations.removePlacement(tenantId, topicId, placementId);
    }

    public List<TopicContentItemResponse> reorderPlacements(
            String tenantId, String topicId, List<ReorderTopicContentItemRequest> requests) {
        return operations.reorderPlacements(tenantId, topicId, requests);
    }
}
