package com.erg.ergspringboot.common.request.hoclieu;

import java.util.Map;

public final class HocLieuStudioRequests {

    private HocLieuStudioRequests() {
    }

    public record TaxonomyNodeRequest(
            String kind,
            String label,
            String slug,
            String parentId,
            String subjectId,
            String categoryId,
            Integer sortOrder,
            String status,
            String description
    ) {
    }

    public record ResourceRequest(
            String title,
            String subtitle,
            String description,
            String thumbnailUrl,
            String subjectId,
            String categoryId,
            String lessonId,
            String sectionId,
            String type,
            String documentTypeId,
            String status,
            String visibility,
            String programSlug
    ) {
    }

    public record AttachResourceRequest(
            String resourceId,
            String title,
            String type,
            String fileType,
            String thumbnailUrl
    ) {
    }

    public record AssetRequest(
            String title,
            String selectedFileType,
            String fileType,
            String launchMode,
            String originalFileName,
            String detectedMimeType,
            String fileExtension,
            Long fileSizeBytes,
            String storageProvider,
            String storageUrl,
            Boolean canDownload,
            String status,
            Map<String, Object> metadata
    ) {
    }

    public record SlideAnnotationRequest(
            String schoolId,
            String academicYear,
            java.util.List<Map<String, Object>> paths
    ) {
    }
}
