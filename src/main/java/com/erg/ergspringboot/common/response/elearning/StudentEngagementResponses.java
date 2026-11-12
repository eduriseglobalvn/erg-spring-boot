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
            List<StudentDiscussionCommentResponse> comments,
            boolean hidden
    ) {
        public static StudentDiscussionPostResponse empty(String id) {
            return new StudentDiscussionPostResponse(
                    id, null, null, null, null, null, null, null, null, List.of(), null, List.of(), null, List.of(), false
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
            List<StudentDiscussionCommentResponse> replies,
            boolean hidden
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

    public record StudentDiscussionModerationClassResponse(String id, String name) {
    }

    public record StudentDiscussionModerationResponse(String targetType, String targetId, boolean hidden, boolean deleted) {
    }

    /**
     * {@code enabled == false} for a student means the teacher switched the board off: entries are empty.
     * {@code periodEndsAt}: when the period's board closes (ISO-8601).
     */
    public record ClassLeaderboardResponse(String classId, String className, boolean enabled, String period,
                                           String scope, String periodEndsAt, boolean viewerHidden,
                                           List<ClassLeaderboardEntryResponse> entries) {
    }

    /** {@code rank} is null with no XP in the period (listed last); {@code rankChange} &gt; 0 means moved up. */
    public record ClassLeaderboardEntryResponse(Integer rank, String studentId, String studentName, int xp,
                                                int streakDays, Integer rankChange, boolean isViewer) {
    }

    public record LeaderboardClassResponse(String id, String name, boolean leaderboardEnabled) {
    }

    public record AnnouncementReadResponse(String announcementId, boolean read, OffsetDateTime readAt) {
    }
}
