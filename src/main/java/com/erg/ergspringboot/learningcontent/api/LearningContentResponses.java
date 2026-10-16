package com.erg.ergspringboot.learningcontent.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Stable learning-content transport contracts. These records deliberately contain only
 * scalar/collection data and never expose persistence entities or repository projections.
 */
public final class LearningContentResponses {

    private LearningContentResponses() {
    }

    public record ContentItemResponse(
            String id,
            String tenantId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String contentType,
            String title,
            String description,
            String status,
            String visibility,
            String ownerId,
            String createdBy,
            String updatedBy
    ) {
    }

    public record ContentAssetResponse(
            String id,
            String tenantId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
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
            String createdBy,
            String updatedBy
    ) {
    }

    public record ContentItemAssetResponse(
            String id,
            String tenantId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String contentItemId,
            String contentAssetId,
            String assetRole,
            Integer sortOrder,
            Map<String, Object> metadata,
            String createdBy
    ) {
    }

    public record TopicContentItemResponse(
            String id,
            String tenantId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String topicId,
            String contentItemId,
            String contentRole,
            Integer sortOrder,
            String status,
            String createdBy
    ) {
    }

    public record GoogleSlidesLectureResponse(
            ContentItemResponse contentItem,
            ContentAssetResponse contentAsset,
            ContentItemAssetResponse assetLink,
            TopicContentItemResponse placement
    ) {
    }

    public record DeletedResponse(boolean deleted) {
    }
}
