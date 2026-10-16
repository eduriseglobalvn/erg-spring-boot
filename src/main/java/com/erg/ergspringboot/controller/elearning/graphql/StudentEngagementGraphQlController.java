package com.erg.ergspringboot.controller.elearning.graphql;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.AnnouncementReadResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionCommentResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionFeedResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionPostResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionReactionResponse;
import com.erg.ergspringboot.elearning.ElearningOperations;
import com.erg.ergspringboot.elearning.api.ElearningCommands.CreateDiscussionCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.DiscussionFeedQuery;
import com.erg.ergspringboot.elearning.api.ElearningCommands.MarkAnnouncementReadCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.ReplyCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.SetReactionCommand;
import com.erg.ergspringboot.elearning.authorization.ElearningAnnouncementReadSelf;
import com.erg.ergspringboot.elearning.authorization.ElearningDashboardRead;
import com.erg.ergspringboot.elearning.authorization.ElearningDiscussionWriteSelf;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class StudentEngagementGraphQlController {

    private final ElearningOperations service;
    private final GraphQlAccessSupport accessSupport;

    public StudentEngagementGraphQlController(ElearningOperations service, GraphQlAccessSupport accessSupport) {
        this.service = service;
        this.accessSupport = accessSupport;
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "discussionFeed")
    @ElearningDashboardRead
    public StudentDiscussionFeedResponse discussionFeed(@Argument StudentDiscussionFeedInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        StudentDiscussionFeedInput safe = input == null ? new StudentDiscussionFeedInput(null, 0, 20) : input;
        return service.discussionFeed(new DiscussionFeedQuery(
                accessSupport.verifiedTenantId(principal, safe.tenantId()), principal.userId(),
                safe.page() == null ? 0 : safe.page(), safe.size() == null ? 20 : safe.size()
        ));
    }

    @MutationMapping
    @ElearningDiscussionWriteSelf
    public StudentDiscussionPostResponse createStudentDiscussion(@Argument CreateStudentDiscussionInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.discussion.write_self");
        return service.createDiscussion(new CreateDiscussionCommand(
                accessSupport.verifiedTenantId(principal, input.tenantId()), principal.userId(), input.idempotencyKey(),
                input.title(), input.content(), input.assignmentId(), safe(input.attachmentIds())
        ));
    }

    @MutationMapping
    @ElearningDiscussionWriteSelf
    public StudentDiscussionCommentResponse replyStudentDiscussion(@Argument ReplyStudentDiscussionInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.discussion.write_self");
        return service.reply(new ReplyCommand(
                accessSupport.verifiedTenantId(principal, input.tenantId()), principal.userId(), input.idempotencyKey(),
                input.postId(), input.parentCommentId(), input.content(), safe(input.attachmentIds())
        ));
    }

    @MutationMapping
    @ElearningDiscussionWriteSelf
    public StudentDiscussionReactionResponse setStudentDiscussionReaction(@Argument SetStudentDiscussionReactionInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.discussion.write_self");
        return service.setReaction(new SetReactionCommand(
                accessSupport.verifiedTenantId(principal, input.tenantId()), principal.userId(),
                input.targetType(), input.targetId(), input.reaction()
        ));
    }

    @MutationMapping
    @ElearningAnnouncementReadSelf
    public AnnouncementReadResponse markStudentAnnouncementRead(@Argument MarkStudentAnnouncementReadInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.announcement.read_self");
        return service.markAnnouncementRead(new MarkAnnouncementReadCommand(
                accessSupport.verifiedTenantId(principal, input.tenantId()), principal.userId(), input.announcementId()
        ));
    }

    private ErgPrincipal requirePrincipal(String permission) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, permission, "*");
        return principal;
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record StudentDiscussionFeedInput(String tenantId, Integer page, Integer size) { }

    public record CreateStudentDiscussionInput(String tenantId, String idempotencyKey, String title, String content,
                                                String assignmentId, List<String> attachmentIds) { }
    public record ReplyStudentDiscussionInput(String tenantId, String idempotencyKey, String postId,
                                               String parentCommentId, String content, List<String> attachmentIds) { }
    public record SetStudentDiscussionReactionInput(String tenantId, String targetType, String targetId, String reaction) { }
    public record MarkStudentAnnouncementReadInput(String tenantId, String announcementId) { }
}
