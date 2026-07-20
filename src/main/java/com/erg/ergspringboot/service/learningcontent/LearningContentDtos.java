package com.erg.ergspringboot.service.learningcontent;

import com.erg.ergspringboot.model.learningcontent.ContentAsset;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.ContentItemAsset;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class LearningContentDtos {

    private LearningContentDtos() {
    }

    public record GoogleSlidesLectureResponse(
            ContentItem contentItem,
            ContentAsset contentAsset,
            ContentItemAsset assetLink,
            TopicContentItem placement
    ) {
    }

    public record ContentItemListResponse(
            List<ContentItemResponse> items,
            long total
    ) {
    }

    public record ContentItemResponse(
            String id,
            String contentType,
            String title,
            String description,
            String status,
            String visibility,
            String ownerId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<ContentAssetResponse> assets,
            List<TopicContentPlacementResponse> placements
    ) {
    }

    public record ContentAssetResponse(
            String id,
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
            String assetRole,
            Integer sortOrder
    ) {
    }

    public record TopicContentPlacementResponse(
            String id,
            String topicId,
            String contentRole,
            Integer sortOrder,
            String status
    ) {
    }
}
