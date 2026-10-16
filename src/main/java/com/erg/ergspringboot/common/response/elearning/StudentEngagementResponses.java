package com.erg.ergspringboot.common.response.elearning;

import java.time.OffsetDateTime;
import java.util.List;

public final class StudentEngagementResponses {

    private StudentEngagementResponses() {
    }

    public record StudentDiscussionFeedResponse(
            List<StudentDiscussionPostResponse> items,
            int page,
            int size,
            long totalItems,
            boolean hasNext
    ) {
    }

    public record StudentDiscussionPostResponse(
            String id,
            String authorId,
            String authorName,
            String authorInitials,
            String academicClassId,
            String className,
            String title,
            String content,
            OffsetDateTime createdAt,
            List<String> attachmentIds,
            String assignmentId,
            List<StudentDiscussionReactionCountResponse> reactions,
            String viewerReaction,
            List<StudentDiscussionCommentResponse> comments
    ) {
        public static StudentDiscussionPostResponse empty(String id) {
            return new StudentDiscussionPostResponse(
                    id, null, null, null, null, null, null, null, null, List.of(), null, List.of(), null, List.of()
            );
        }
    }

    public record StudentDiscussionCommentResponse(
            String id,
            String parentId,
            String authorId,
            String authorName,
            String authorInitials,
            String content,
            OffsetDateTime createdAt,
            List<String> attachmentIds,
            List<StudentDiscussionReactionCountResponse> reactions,
            String viewerReaction,
            List<StudentDiscussionCommentResponse> replies
    ) {
    }

    public record StudentDiscussionReactionCountResponse(String reaction, int count) {
    }

    public record StudentDiscussionReactionResponse(
            String targetType,
            String targetId,
            String viewerReaction,
            List<StudentDiscussionReactionCountResponse> reactions
    ) {
    }

    public record AnnouncementReadResponse(String announcementId, boolean read, OffsetDateTime readAt) {
    }
}
