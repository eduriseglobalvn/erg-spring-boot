package com.erg.ergspringboot.common.response.learningcontent;

import com.erg.ergspringboot.common.response.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;

public final class LearningResourceLibraryResponses {

    private LearningResourceLibraryResponses() {
    }

    public record LearningResourceLibraryResponse(
            String tenantId,
            String educationUnitId,
            List<LearningResourceTaxonomyNodeResponse> taxonomyTree,
            PageResponse<LearningResourceCardResponse> resources,
            List<LearningResourceProgressResponse> progress,
            List<LearningRecentResourceResponse> recentOpened
    ) {
    }

    public record LearningResourceTaxonomyNodeResponse(
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

    public record LearningResourceCardResponse(
            String id,
            String subjectId,
            String gradeId,
            String categoryId,
            String sectionId,
            String topicId,
            String title,
            String slug,
            String subtitle,
            String thumbnailUrl,
            String visibility,
            String status,
            OffsetDateTime publishedAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record LearningResourceProgressResponse(
            String resourceId,
            int progressRate,
            OffsetDateTime updatedAt
    ) {
    }

    public record LearningRecentResourceResponse(
            String resourceId,
            String title,
            OffsetDateTime openedAt
    ) {
    }
}
