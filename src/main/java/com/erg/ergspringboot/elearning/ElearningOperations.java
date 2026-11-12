package com.erg.ergspringboot.elearning;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.AnnouncementReadResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionCommentResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionFeedResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionModerationClassResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionModerationResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionPostResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionReactionResponse;
import com.erg.ergspringboot.elearning.api.ElearningCommands.CreateDiscussionCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.DashboardReadQuery;
import com.erg.ergspringboot.elearning.api.ElearningCommands.DiscussionFeedQuery;
import com.erg.ergspringboot.elearning.api.ElearningCommands.DiscussionModerator;
import com.erg.ergspringboot.elearning.api.ElearningCommands.MarkAnnouncementReadCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.ReplyCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.SetReactionCommand;
import com.erg.ergspringboot.elearning.api.StudentDashboardReadScope;
import com.erg.ergspringboot.elearning.internal.engagement.StudentDiscussionModerationService;
import com.erg.ergspringboot.elearning.internal.engagement.StudentEngagementService;
import com.erg.ergspringboot.elearning.internal.readmodel.StudentDashboardReadService;
import org.springframework.stereotype.Service;

/**
 * Public ELEARNING application boundary for persistent student flows; it
 * forwards to the module-internal dashboard and engagement services.
 */
@Service
public class ElearningOperations {

    private final StudentDashboardReadService dashboard;
    private final StudentEngagementService engagement;
    private final StudentDiscussionModerationService moderation;

    public ElearningOperations(
            StudentDashboardReadService dashboard,
            StudentEngagementService engagement,
            StudentDiscussionModerationService moderation
    ) {
        this.dashboard = dashboard;
        this.engagement = engagement;
        this.moderation = moderation;
    }

    public StudentDashboardResponse studentDashboard(DashboardReadQuery query) {
        return dashboard.studentDashboard(new StudentDashboardReadScope(
                query.userId(), query.tenantId(), query.requestedTenantId(), query.requestedStudentUserId(),
                safe(query.roles()), safe(query.permissions()), safe(query.deniedPermissions()), safe(query.portals()),
                query.accountType(), query.accessLevel(), query.requestedClassId()));
    }

    public StudentDiscussionFeedResponse discussionFeed(DiscussionFeedQuery query) {
        return engagement.feed(new StudentEngagementService.FeedScope(
                query.tenantId(), query.actorUserId(), query.page(), query.size()));
    }

    public StudentDiscussionPostResponse createDiscussion(CreateDiscussionCommand command) {
        return engagement.createDiscussion(new StudentEngagementService.CreateDiscussionCommand(
                command.tenantId(), command.actorUserId(), command.idempotencyKey(), command.title(), command.content(),
                command.assignmentId(), safe(command.attachmentIds())));
    }

    public StudentDiscussionCommentResponse reply(ReplyCommand command) {
        return engagement.reply(new StudentEngagementService.ReplyCommand(
                command.tenantId(), command.actorUserId(), command.idempotencyKey(), command.postId(),
                command.parentCommentId(), command.content(), safe(command.attachmentIds())));
    }

    public StudentDiscussionReactionResponse setReaction(SetReactionCommand command) {
        return engagement.setReaction(new StudentEngagementService.SetReactionCommand(
                command.tenantId(), command.actorUserId(), command.targetType(), command.targetId(), command.reaction()));
    }

    public java.util.List<StudentDiscussionModerationClassResponse> discussionModerationClasses(DiscussionModerator moderator) {
        return moderation.classes(moderator(moderator));
    }

    public StudentDiscussionFeedResponse discussionModerationFeed(DiscussionModerator moderator, String classId, int page, int size) {
        return moderation.feed(moderator(moderator), classId, page, size);
    }

    public StudentDiscussionModerationResponse setDiscussionHidden(
            DiscussionModerator moderator, String targetType, String targetId, boolean hidden) {
        return moderation.setHidden(moderator(moderator), targetType, targetId, hidden);
    }

    public StudentDiscussionModerationResponse deleteDiscussionItem(DiscussionModerator moderator, String targetType, String targetId) {
        return moderation.delete(moderator(moderator), targetType, targetId);
    }

    public AnnouncementReadResponse markAnnouncementRead(MarkAnnouncementReadCommand command) {
        return engagement.markAnnouncementRead(new StudentEngagementService.MarkAnnouncementReadCommand(
                command.tenantId(), command.actorUserId(), command.announcementId()));
    }

    private StudentDiscussionModerationService.Moderator moderator(DiscussionModerator moderator) {
        return new StudentDiscussionModerationService.Moderator(moderator.tenantId(), moderator.userId(), moderator.tenantWide());
    }

    private <T> java.util.List<T> safe(java.util.List<T> values) {
        return values == null ? java.util.List.of() : java.util.List.copyOf(values);
    }
}
