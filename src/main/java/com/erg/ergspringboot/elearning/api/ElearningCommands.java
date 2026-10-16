package com.erg.ergspringboot.elearning.api;

import java.util.List;

/** Module-owned command/query inputs for student ELEARNING flows. */
public final class ElearningCommands {

    private ElearningCommands() {
    }

    public record DashboardReadQuery(
            String userId,
            String tenantId,
            String requestedTenantId,
            String requestedStudentUserId,
            List<String> roles,
            List<String> permissions,
            List<String> deniedPermissions,
            List<String> portals,
            String accountType,
            String accessLevel,
            String requestedClassId
    ) {
    }

    public record DiscussionFeedQuery(String tenantId, String actorUserId, int page, int size) {
    }

    public record CreateDiscussionCommand(
            String tenantId,
            String actorUserId,
            String idempotencyKey,
            String title,
            String content,
            String assignmentId,
            List<String> attachmentIds
    ) {
    }

    public record ReplyCommand(
            String tenantId,
            String actorUserId,
            String idempotencyKey,
            String postId,
            String parentCommentId,
            String content,
            List<String> attachmentIds
    ) {
    }

    public record SetReactionCommand(
            String tenantId,
            String actorUserId,
            String targetType,
            String targetId,
            String reaction
    ) {
    }

    public record MarkAnnouncementReadCommand(
            String tenantId,
            String actorUserId,
            String announcementId
    ) {
    }
}
