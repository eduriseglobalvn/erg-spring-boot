package com.erg.ergspringboot.common.response.elearning;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * The part of one discussion feed page that every viewer of the class shares (posts, the comment window, reaction counts and
 * visible-comment totals); cached for a few seconds. Viewer-specific reactions and follows are never in it.
 */
public record DiscussionFeedSnapshot(List<Post> posts, List<Comment> comments, List<ReactionCount> reactions,
                                     Map<String, Integer> commentTotals, Map<String, Integer> topLevelTotals,
                                     Map<String, Integer> replyTotals, long totalItems) {

    public record Post(String id, String authorId, String authorName, String academicClassId, String className, String title,
                       String content, OffsetDateTime createdAt, String attachmentIdsJson, String assignmentId, boolean hidden,
                       String subject, boolean pinned, String acceptedCommentId) { }

    public record Comment(String id, String postId, String parentId, String authorId, String authorName, String content,
                          OffsetDateTime createdAt, String attachmentIdsJson, boolean hidden) { }

    /** {@code targetType} is POST or COMMENT. */
    public record ReactionCount(String targetType, String targetId, String reaction, int count) { }
}
