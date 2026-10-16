package com.erg.ergspringboot.learningcontent.internal;

import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.ContentAssetResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.ContentItemAssetResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.ContentItemResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.GoogleSlidesLectureResponse;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.TopicContentItemResponse;
import com.erg.ergspringboot.model.learningcontent.ContentAsset;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.ContentItemAsset;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import com.erg.ergspringboot.service.learningcontent.LearningContentDtos;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure entity-to-transport assembler kept inside the learning-content adapter. */
public final class LearningContentAssembler {

    private LearningContentAssembler() {
    }

    public static ContentItemResponse contentItem(ContentItem item) {
        return new ContentItemResponse(
                item.getId(), item.getTenantId(), item.getCreatedAt(), item.getUpdatedAt(),
                item.getContentType(), item.getTitle(), item.getDescription(), item.getStatus(),
                item.getVisibility(), item.getOwnerId(), item.getCreatedBy(), item.getUpdatedBy());
    }

    public static ContentAssetResponse contentAsset(ContentAsset asset) {
        return new ContentAssetResponse(
                asset.getId(), asset.getTenantId(), asset.getCreatedAt(), asset.getUpdatedAt(),
                asset.getAssetType(), asset.getTitle(), asset.getStorageProvider(), asset.getStorageKey(),
                asset.getStorageUrl(), asset.getMimeType(), asset.getByteSize(), asset.getChecksumSha256(),
                asset.getStatus(), copy(asset.getMetadata()), asset.getCreatedBy(), asset.getUpdatedBy());
    }

    public static ContentItemAssetResponse contentItemAsset(ContentItemAsset link) {
        return new ContentItemAssetResponse(
                link.getId(), link.getTenantId(), link.getCreatedAt(), link.getUpdatedAt(),
                link.getContentItemId(), link.getContentAssetId(), link.getAssetRole(), link.getSortOrder(),
                copy(link.getMetadata()), link.getCreatedBy());
    }

    public static TopicContentItemResponse topicContentItem(TopicContentItem placement) {
        return new TopicContentItemResponse(
                placement.getId(), placement.getTenantId(), placement.getCreatedAt(), placement.getUpdatedAt(),
                placement.getTopicId(), placement.getContentItemId(), placement.getContentRole(),
                placement.getSortOrder(), placement.getStatus(), placement.getCreatedBy());
    }

    public static GoogleSlidesLectureResponse googleSlidesLecture(
            LearningContentDtos.GoogleSlidesLectureResponse response) {
        return new GoogleSlidesLectureResponse(
                contentItem(response.contentItem()),
                contentAsset(response.contentAsset()),
                contentItemAsset(response.assetLink()),
                topicContentItem(response.placement()));
    }

    private static Map<String, Object> copy(Map<String, Object> metadata) {
        return metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    }
}
