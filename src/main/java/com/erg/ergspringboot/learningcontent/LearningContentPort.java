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

/** Public command/query boundary for learning-content transport operations. */
public interface LearningContentPort {

    GoogleSlidesLectureResponse createGoogleSlidesLecture(
            String tenantId, String userId, CreateGoogleSlidesLectureRequest request);

    ContentItemResponse createContentItem(
            String tenantId, String userId, CreateContentItemRequest request);

    ContentItemResponse updateContentItem(
            String tenantId, String contentItemId, String userId, UpdateContentItemRequest request);

    DeletedResponse deleteContentItem(String tenantId, String contentItemId);

    ContentAssetResponse createContentAsset(
            String tenantId, String userId, CreateContentAssetRequest request);

    ContentAssetResponse updateContentAsset(
            String tenantId, String contentAssetId, String userId, UpdateContentAssetRequest request);

    ContentItemAssetResponse attachContentAsset(
            String tenantId, String contentItemId, String userId, AttachContentAssetRequest request);

    TopicContentItemResponse placeContentItem(
            String tenantId, String topicId, String userId, PlaceContentItemRequest request);

    DeletedResponse removePlacement(String tenantId, String topicId, String placementId);

    List<TopicContentItemResponse> reorderPlacements(
            String tenantId, String topicId, List<ReorderTopicContentItemRequest> requests);
}
