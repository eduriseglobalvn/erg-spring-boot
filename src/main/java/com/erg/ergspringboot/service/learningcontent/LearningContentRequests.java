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
            String visibility,
            Long expectedVersion,
            String operationId,
            String idempotencyKey
    ) {
        public UpdateContentItemRequest(String contentType, String title, String description,
                                        String status, String visibility) {
            this(contentType, title, description, status, visibility, null, null, null);
        }

        public UpdateContentItemRequest withIdempotencyKey(String key) {
            return new UpdateContentItemRequest(contentType, title, description, status, visibility,
                    expectedVersion, operationId, key);
        }
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
            Map<String, Object> metadata,
            Long expectedVersion,
            String operationId,
            String idempotencyKey
    ) {
        public UpdateContentAssetRequest(String assetType, String title, String storageProvider,
                                         String storageKey, String storageUrl, String mimeType, Long byteSize,
                                         String checksumSha256, String status, Map<String, Object> metadata) {
            this(assetType, title, storageProvider, storageKey, storageUrl, mimeType, byteSize,
                    checksumSha256, status, metadata, null, null, null);
        }

        public UpdateContentAssetRequest withIdempotencyKey(String key) {
            return new UpdateContentAssetRequest(assetType, title, storageProvider, storageKey, storageUrl,
                    mimeType, byteSize, checksumSha256, status, metadata, expectedVersion, operationId, key);
        }
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
