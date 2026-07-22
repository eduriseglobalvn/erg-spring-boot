package com.erg.ergspringboot.controller.learningcontent;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.model.learningcontent.ContentAsset;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.ContentItemAsset;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.AttachContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateGoogleSlidesLectureRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos.GoogleSlidesLectureResponse;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.PlaceContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.ReorderTopicContentItemsRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.UpdateContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.UpdateContentItemRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LearningContentController {

    private final LearningContentService learningContentService;

    public LearningContentController(LearningContentService learningContentService) {
        this.learningContentService = learningContentService;
    }

    @PostMapping({"/api/content/lectures/google-slides", "/api/v1/content/lectures/google-slides"})
    public ApiResponse<GoogleSlidesLectureResponse> createGoogleSlidesLecture(
            @RequestBody CreateGoogleSlidesLectureRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        GoogleSlidesLectureResponse lecture = learningContentService.createGoogleSlidesLecture(
                access.tenantId(),
                access.userId(),
                body
        );
        return ok(201, "Google Slides lecture created", lecture, request);
    }

    @PostMapping({"/api/content/items", "/api/v1/content/items"})
    public ApiResponse<ContentItem> createContentItem(
            @RequestBody CreateContentItemRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        ContentItem item = learningContentService.createContentItem(access.tenantId(), access.userId(), body);
        return ok(201, "Content item created", item, request);
    }

    @PatchMapping({"/api/content/items/{contentItemId}", "/api/v1/content/items/{contentItemId}"})
    public ApiResponse<ContentItem> updateContentItem(
            @PathVariable String contentItemId,
            @RequestBody UpdateContentItemRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        ContentItem item = learningContentService.updateContentItem(
                access.tenantId(),
                contentItemId,
                access.userId(),
                body
        );
        return ok(200, "Content item updated", item, request);
    }

    @DeleteMapping({"/api/content/items/{contentItemId}", "/api/v1/content/items/{contentItemId}"})
    public ApiResponse<Map<String, Boolean>> deleteContentItem(
            @PathVariable String contentItemId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        learningContentService.deleteContentItem(access.tenantId(), contentItemId);
        return ok(200, "Content item deleted", Map.of("deleted", true), request);
    }

    @PostMapping({"/api/content/assets", "/api/v1/content/assets"})
    public ApiResponse<ContentAsset> createContentAsset(
            @RequestBody CreateContentAssetRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        ContentAsset asset = learningContentService.createContentAsset(access.tenantId(), access.userId(), body);
        return ok(201, "Content asset created", asset, request);
    }

    @PatchMapping({"/api/content/assets/{contentAssetId}", "/api/v1/content/assets/{contentAssetId}"})
    public ApiResponse<ContentAsset> updateContentAsset(
            @PathVariable String contentAssetId,
            @RequestBody UpdateContentAssetRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        ContentAsset asset = learningContentService.updateContentAsset(
                access.tenantId(),
                contentAssetId,
                access.userId(),
                body
        );
        return ok(200, "Content asset updated", asset, request);
    }

    @PostMapping({"/api/content/items/{contentItemId}/assets", "/api/v1/content/items/{contentItemId}/assets"})
    public ApiResponse<ContentItemAsset> attachContentAsset(
            @PathVariable String contentItemId,
            @RequestBody AttachContentAssetRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        ContentItemAsset asset = learningContentService.attachContentAsset(
                access.tenantId(),
                contentItemId,
                access.userId(),
                body
        );
        return ok(201, "Content asset attached", asset, request);
    }

    @PostMapping({"/api/curriculum/topics/{topicId}/content-items", "/api/v1/curriculum/topics/{topicId}/content-items"})
    public ApiResponse<TopicContentItem> placeContentItem(
            @PathVariable String topicId,
            @RequestBody PlaceContentItemRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        TopicContentItem placement = learningContentService.placeContentItem(
                access.tenantId(),
                topicId,
                access.userId(),
                body
        );
        return ok(201, "Content item placed into topic", placement, request);
    }

    @DeleteMapping({"/api/curriculum/topics/{topicId}/content-items/{placementId}", "/api/v1/curriculum/topics/{topicId}/content-items/{placementId}"})
    public ApiResponse<Void> removePlacement(
            @PathVariable String topicId,
            @PathVariable String placementId,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        learningContentService.removePlacement(access.tenantId(), topicId, placementId);
        return ok(200, "Content placement removed", null, request);
    }

    @PatchMapping({"/api/curriculum/topics/{topicId}/content-items/reorder", "/api/v1/curriculum/topics/{topicId}/content-items/reorder"})
    public ApiResponse<List<TopicContentItem>> reorderPlacements(
            @PathVariable String topicId,
            @RequestBody ReorderTopicContentItemsRequest body,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        List<TopicContentItem> placements = learningContentService.reorderPlacements(
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
        Object requestId = request.getAttribute("requestId");
        return requestId == null ? request.getHeader("X-Request-ID") : requestId.toString();
    }
}
