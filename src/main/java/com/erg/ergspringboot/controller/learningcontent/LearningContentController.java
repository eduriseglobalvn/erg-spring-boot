package com.erg.ergspringboot.controller.learningcontent;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.learningcontent.LearningContentOperations;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.ContentAssetResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.ContentItemAssetResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.ContentItemResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.DeletedResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.GoogleSlidesLectureResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.TopicContentItemResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.AttachContentAssetRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.CreateContentAssetRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.CreateGoogleSlidesLectureRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.CreateContentItemRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.PlaceContentItemRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.ReorderTopicContentItemsRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.UpdateContentAssetRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.UpdateContentItemRequest;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LearningContentController {

    private final LearningContentOperations learningContentOperations;

    public LearningContentController(LearningContentOperations learningContentOperations) {
        this.learningContentOperations = learningContentOperations;
    }

    @PostMapping({"/api/content/lectures/google-slides", "/api/v1/content/lectures/google-slides"})
    public ApiResponse<GoogleSlidesLectureResponse> createGoogleSlidesLecture(
            @RequestBody CreateGoogleSlidesLectureRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        GoogleSlidesLectureResponse lecture = learningContentOperations.createGoogleSlidesLecture(
                access.tenantId(),
                access.userId(),
                body
        );
        return ok(201, "Google Slides lecture created", lecture, request);
    }

    @PostMapping({"/api/content/items", "/api/v1/content/items"})
    public ApiResponse<ContentItemResponse> createContentItem(
            @RequestBody CreateContentItemRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        ContentItemResponse item = learningContentOperations.createContentItem(access.tenantId(), access.userId(), body);
        return ok(201, "Content item created", item, request);
    }

    @PatchMapping({"/api/content/items/{contentItemId}", "/api/v1/content/items/{contentItemId}"})
    public ApiResponse<ContentItemResponse> updateContentItem(
            @PathVariable String contentItemId,
            @RequestBody UpdateContentItemRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        UpdateContentItemRequest effectiveBody = body == null || idempotencyKey == null || idempotencyKey.isBlank()
                ? body
                : body.withIdempotencyKey(idempotencyKey);
        ContentItemResponse item = learningContentOperations.updateContentItem(
                access.tenantId(),
                contentItemId,
                access.userId(),
                effectiveBody
        );
        return ok(200, "Content item updated", item, request);
    }

    @DeleteMapping({"/api/content/items/{contentItemId}", "/api/v1/content/items/{contentItemId}"})
    public ApiResponse<DeletedResponse> deleteContentItem(
            @PathVariable String contentItemId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        DeletedResponse result = learningContentOperations.deleteContentItem(access.tenantId(), contentItemId);
        return ok(200, "Content item deleted", result, request);
    }

    @PostMapping({"/api/content/assets", "/api/v1/content/assets"})
    public ApiResponse<ContentAssetResponse> createContentAsset(
            @RequestBody CreateContentAssetRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        ContentAssetResponse asset = learningContentOperations.createContentAsset(access.tenantId(), access.userId(), body);
        return ok(201, "Content asset created", asset, request);
    }

    @PatchMapping({"/api/content/assets/{contentAssetId}", "/api/v1/content/assets/{contentAssetId}"})
    public ApiResponse<ContentAssetResponse> updateContentAsset(
            @PathVariable String contentAssetId,
            @RequestBody UpdateContentAssetRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        UpdateContentAssetRequest effectiveBody = body == null || idempotencyKey == null || idempotencyKey.isBlank()
                ? body
                : body.withIdempotencyKey(idempotencyKey);
        ContentAssetResponse asset = learningContentOperations.updateContentAsset(
                access.tenantId(),
                contentAssetId,
                access.userId(),
                effectiveBody
        );
        return ok(200, "Content asset updated", asset, request);
    }

    @PostMapping({"/api/content/items/{contentItemId}/assets", "/api/v1/content/items/{contentItemId}/assets"})
    public ApiResponse<ContentItemAssetResponse> attachContentAsset(
            @PathVariable String contentItemId,
            @RequestBody AttachContentAssetRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        ContentItemAssetResponse asset = learningContentOperations.attachContentAsset(
                access.tenantId(),
                contentItemId,
                access.userId(),
                body
        );
        return ok(201, "Content asset attached", asset, request);
    }

    @PostMapping({"/api/curriculum/topics/{topicId}/content-items", "/api/v1/curriculum/topics/{topicId}/content-items"})
    public ApiResponse<TopicContentItemResponse> placeContentItem(
            @PathVariable String topicId,
            @RequestBody PlaceContentItemRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        TopicContentItemResponse placement = learningContentOperations.placeContentItem(
                access.tenantId(),
                topicId,
                access.userId(),
                body
        );
        return ok(201, "Content item placed into topic", placement, request);
    }

    @DeleteMapping({"/api/curriculum/topics/{topicId}/content-items/{placementId}", "/api/v1/curriculum/topics/{topicId}/content-items/{placementId}"})
    public ApiResponse<DeletedResponse> removePlacement(
            @PathVariable String topicId,
            @PathVariable String placementId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        DeletedResponse result = learningContentOperations.removePlacement(access.tenantId(), topicId, placementId);
        return ok(200, "Content placement removed", result, request);
    }

    @PatchMapping({"/api/curriculum/topics/{topicId}/content-items/reorder", "/api/v1/curriculum/topics/{topicId}/content-items/reorder"})
    public ApiResponse<List<TopicContentItemResponse>> reorderPlacements(
            @PathVariable String topicId,
            @RequestBody ReorderTopicContentItemsRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        List<TopicContentItemResponse> placements = learningContentOperations.reorderPlacements(
                access.tenantId(),
                topicId,
                body == null ? null : body.placements()
        );
        return ok(200, "Content placements reordered", placements, request);
    }

    private <T> ApiResponse<T> ok(int statusCode, String message, T data, HttpServletRequest request) {
        return ApiResponse.success(statusCode, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}
