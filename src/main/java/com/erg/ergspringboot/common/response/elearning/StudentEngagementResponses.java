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
            boolean hidden,
            String subject,
            boolean pinned,
            String acceptedReplyId,
            boolean following
    ) {
        public static StudentDiscussionPostResponse empty(String id) {
            return new StudentDiscussionPostResponse(
                    id, null, null, null, null, null, null, null, null, List.of(), null, List.of(), null, List.of(), false, null, false, null, false
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

    /** One Hộp thư message; {@code mine} is true for the viewer's own side. */
    public record QuestionMessageResponse(String id, String senderId, String senderName, String senderRole, boolean mine,
                                          String body, List<String> attachmentIds, OffsetDateTime createdAt) {
    }

    /**
     * Student-to-teacher thread tied to one question ({@code questionIndex} is 1-based).
     * {@code correctAnswerText} is null unless the quiz lets the student see answers and they handed in.
     */
    public record QuestionThreadResponse(String id, String assignmentId, String assignmentTitle, String subjectName,
                                         String className, int questionIndex, String questionPrompt, String studentAnswerText,
                                         String correctAnswerText, String teacherId, String teacherName, String studentName,
                                         String status, OffsetDateTime lastMessageAt, String lastMessagePreview,
                                         List<QuestionMessageResponse> messages, boolean unread) {
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
                                           List<ClassLeaderboardEntryResponse> entries, LeaderboardMeResponse me) {
    }

    /**
     * The viewer's standing for the "Thứ hạng của bạn" strip. {@code rank}/{@code size}: in the requested scope
     * ({@code size} = students listed, rank is null with no XP); {@code gradeRank}/{@code gradeSize}: the whole grade.
     * {@code xpToNextRank}: XP to pass the student ranked just above; null at rank 1 or without a rank.
     */
    public record LeaderboardMeResponse(Integer rank, int size, int xp, Integer gradeRank, int gradeSize,
                                        Integer xpToNextRank, String nextRankName) {
    }

    /** {@code rank} is null with no XP in the period (listed last); {@code rankChange} &gt; 0 means moved up since the previous period,
     * {@code rankDelta} &gt; 0 since yesterday (null when unranked yesterday). */
    public record ClassLeaderboardEntryResponse(Integer rank, String studentId, String studentName, int xp,
                                                int streakDays, Integer rankChange, boolean isViewer,
                                                Integer rankDelta, int submittedInPeriod, int xpToday,
                                                String tierKey, Integer division, String frameKey, boolean hidden) {
    }

    public record LeaderboardClassResponse(String id, String name, boolean leaderboardEnabled) {
    }

    /** Dates are ISO yyyy-MM-dd strings (the schema has no Date scalar). */
    public record MyTierResponse(String tierKey, Integer division, Integer gradeRank, Integer gradeSize, int courseXp,
                                 boolean eligible, int submittedCount, boolean protectedFromDrop, TierStepResponse nextDivision,
                                 TierStepResponse nextTier, TierPromotionResponse pendingPromotion) {
        /** GraphQL field {@code protected}: a Java record cannot have a component with that name. */
        public boolean getProtected() {
            return protectedFromDrop;
        }
    }

    public record TierStepResponse(String tierKey, Integer division, int minXp, int studentsToPass) {
    }

    public record TierPromotionResponse(String weekStart, String fromKey, Integer fromDivision, String toKey, Integer toDivision,
                                        int studentsPassed, int gradePercentile) {
    }

    public record TierTableRowResponse(String tierKey, String share, int seats, List<TierCellResponse> divisions) {
    }

    public record TierCellResponse(Integer division, int rankFrom, int rankTo, int minXp) {
    }

    public record FrameResponse(String tierKey, boolean unlocked, String unlockedAt, String schoolYear, Integer studentsToPass) {
    }

    public record MyFramesResponse(String selected, List<FrameResponse> frames) {
    }

    public record AnnouncementReadResponse(String announcementId, boolean read, OffsetDateTime readAt) {
    }
}
