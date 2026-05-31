package com.erg.ergspringboot.common.response.hoclieu;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class HocLieuLibraryResponses {

    private HocLieuLibraryResponses() {
    }

    public record LibraryBootstrapResponse(
            String schoolId,
            String academicYear,
            List<LibrarySubjectResponse> subjects
    ) {
    }

    public record LibrarySubjectResponse(
            String id,
            String label,
            List<LibraryGroupResponse> groups
    ) {
    }

    public record LibraryGroupResponse(
            String id,
            String label,
            List<LibraryLessonResponse> lessons
    ) {
    }

    public record LibraryLessonResponse(
            String id,
            String label,
            List<LibraryResourceResponse> resources
    ) {
    }

    public record LibraryProgressResponse(
            String schoolId,
            String academicYear,
            List<LibraryLessonProgressResponse> lessons
    ) {
    }

    public record LibraryLessonProgressResponse(
            String lessonId,
            int progressRate
    ) {
    }

    public record LibraryResourceResponse(
            String id,
            String title,
            String type,
            String fileType,
            String thumbnailUrl,
            String launchUrl
    ) {
    }

    public record StudioBootstrapResponse(
            List<TaxonomyOptionResponse> subjects,
            List<TaxonomyOptionResponse> groups,
            List<TaxonomyOptionResponse> lessons,
            List<StudioResourceResponse> resources,
            List<String> fileTypes,
            List<DesignerPresetResponse> designerPresets
    ) {
    }

    public record TaxonomyOptionResponse(
            String id,
            String label,
            String slug,
            String parentId,
            String subjectId,
            String categoryId,
            int sortOrder,
            String status
    ) {
    }

    public record StudioResourceResponse(
            String id,
            String title,
            String subjectId,
            String groupId,
            String lessonId,
            String fileType,
            String status,
            String visibility,
            OffsetDateTime updatedAt
    ) {
    }

    public record DesignerPresetResponse(
            String id,
            String name,
            String description,
            String accentColor,
            String layout
    ) {
    }

    public record ResourceListResponse(
            List<StudioResourceResponse> items,
            Map<String, Object> scope
    ) {
    }

    public record StudioTaxonomyNodeResponse(
            String id,
            String kind,
            String label,
            String slug,
            String parentId,
            String subjectId,
            String categoryId,
            int sortOrder,
            String status,
            String description,
            long childCount,
            long resourceCount
    ) {
    }

    public record StudioAssetResponse(
            String id,
            String resourceId,
            String title,
            String fileType,
            String launchMode,
            String originalFileName,
            String mimeType,
            String extension,
            Long sizeBytes,
            String storageProvider,
            String storageUrl,
            boolean canDownload,
            String status
    ) {
    }

    public record SlideManifestResponse(
            String assetId,
            String resourceId,
            String title,
            String fileType,
            String launchMode,
            int slideCount,
            List<SlideResponse> slides,
            boolean canDownload,
            String embedUrl,
            String fallbackReason
    ) {
    }

    public record SlideResponse(
            String id,
            int index,
            String title,
            String imageUrl,
            String thumbnailUrl
    ) {
    }

    public record SlideAnnotationResponse(
            String assetId,
            int slideIndex,
            String schoolId,
            String academicYear,
            List<Map<String, Object>> paths
    ) {
    }
}
