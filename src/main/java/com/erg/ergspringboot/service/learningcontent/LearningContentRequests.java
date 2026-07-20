package com.erg.ergspringboot.service.learningcontent;

import java.util.List;
import java.util.Map;

public final class LearningContentRequests {

    private LearningContentRequests() {
    }

    public record CreateContentItemRequest(
            String contentType,
            String title,
            String description,
            String status,
            String visibility
    ) {
    }

    public record CreateGoogleSlidesLectureRequest(
            String topicId,
            String title,
            String googleSlidesUrl,
            String status,
            String visibility
    ) {
    }

    public record UpdateContentItemRequest(
            String contentType,
            String title,
            String description,
            String status,
            String visibility
    ) {
    }

    public record CreateContentAssetRequest(
            String assetType,
            String title,
            String storageProvider,
            String storageKey,
            String storageUrl,
            String mimeType,
            Long byteSize,
            String checksumSha256,
            String status,
            Map<String, Object> metadata
    ) {
    }

    public record UpdateContentAssetRequest(
            String assetType,
            String title,
            String storageProvider,
            String storageKey,
            String storageUrl,
            String mimeType,
            Long byteSize,
            String checksumSha256,
            String status,
            Map<String, Object> metadata
    ) {
    }

    public record AttachContentAssetRequest(
            String contentAssetId,
            String assetRole,
            Integer sortOrder,
            Map<String, Object> metadata
    ) {
    }

    public record PlaceContentItemRequest(
            String contentItemId,
            String contentRole,
            Integer sortOrder,
            String status
    ) {
    }

    public record ReorderTopicContentItemRequest(
            String placementId,
            Integer sortOrder
    ) {
    }

    public record ReorderTopicContentItemsRequest(
            List<ReorderTopicContentItemRequest> placements
    ) {
    }
}
