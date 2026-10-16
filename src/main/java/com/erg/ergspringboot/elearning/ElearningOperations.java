package com.erg.ergspringboot.elearning;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.AnnouncementReadResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionCommentResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionFeedResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionPostResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionReactionResponse;
import com.erg.ergspringboot.elearning.api.ElearningCommands.CreateDiscussionCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.DashboardReadQuery;
import com.erg.ergspringboot.elearning.api.ElearningCommands.DiscussionFeedQuery;
import com.erg.ergspringboot.elearning.api.ElearningCommands.MarkAnnouncementReadCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.ReplyCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.SetReactionCommand;
import com.erg.ergspringboot.service.elearning.engagement.StudentEngagementService;
import com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadScope;
import com.erg.ergspringboot.service.elearning.readmodel.StudentDashboardReadService;
import org.springframework.stereotype.Service;

/**
 * Public ELEARNING application boundary for persistent student flows. The
 * existing services are compatibility adapters until their internals move into
 * this module.
 */
@Service
public class ElearningOperations {

    private final StudentDashboardReadService dashboard;
    private final StudentEngagementService engagement;

    public ElearningOperations(
            StudentDashboardReadService dashboard,
            StudentEngagementService engagement
    ) {
        this.dashboard = dashboard;
        this.engagement = engagement;
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

    public AnnouncementReadResponse markAnnouncementRead(MarkAnnouncementReadCommand command) {
        return engagement.markAnnouncementRead(new StudentEngagementService.MarkAnnouncementReadCommand(
                command.tenantId(), command.actorUserId(), command.announcementId()));
    }

    private <T> java.util.List<T> safe(java.util.List<T> values) {
        return values == null ? java.util.List.of() : java.util.List.copyOf(values);
    }
}
