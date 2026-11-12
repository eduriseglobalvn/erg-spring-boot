package com.erg.ergspringboot.controller.elearning.graphql;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.AnnouncementReadResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.ClassLeaderboardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.LeaderboardClassResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionCommentResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionFeedResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionModerationClassResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionModerationResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionPostResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionReactionResponse;
import com.erg.ergspringboot.elearning.ElearningOperations;
import com.erg.ergspringboot.elearning.api.ElearningCommands.CreateDiscussionCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.DiscussionFeedQuery;
import com.erg.ergspringboot.elearning.api.ElearningCommands.DiscussionModerator;
import com.erg.ergspringboot.elearning.api.ElearningCommands.LeaderboardManager;
import com.erg.ergspringboot.elearning.api.ElearningCommands.MarkAnnouncementReadCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.ReplyCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.SetReactionCommand;
import com.erg.ergspringboot.elearning.authorization.ElearningAnnouncementReadSelf;
import com.erg.ergspringboot.elearning.authorization.ElearningDashboardRead;
import com.erg.ergspringboot.elearning.authorization.ElearningDiscussionModerate;
import com.erg.ergspringboot.elearning.authorization.ElearningDiscussionWriteSelf;
import com.erg.ergspringboot.elearning.authorization.ElearningLeaderboardManage;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.WorkspaceCapabilityMapper;
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

    @SchemaMapping(typeName = "ElearningQuery", field = "discussionModerationClasses")
    @ElearningDiscussionModerate
    public List<StudentDiscussionModerationClassResponse> discussionModerationClasses(@Argument String tenantId) {
        return service.discussionModerationClasses(moderator(tenantId));
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "discussionModerationFeed")
    @ElearningDiscussionModerate
    public StudentDiscussionFeedResponse discussionModerationFeed(@Argument StudentDiscussionModerationFeedInput input) {
        return service.discussionModerationFeed(moderator(input.tenantId()), input.classId(),
                input.page() == null ? 0 : input.page(), input.size() == null ? 20 : input.size());
    }

    @MutationMapping
    @ElearningDiscussionModerate
    public StudentDiscussionModerationResponse setStudentDiscussionHidden(@Argument SetStudentDiscussionHiddenInput input) {
        return service.setDiscussionHidden(moderator(input.tenantId()), input.targetType(), input.targetId(),
                Boolean.TRUE.equals(input.hidden()));
    }

    @MutationMapping
    @ElearningDiscussionModerate
    public StudentDiscussionModerationResponse deleteStudentDiscussionItem(@Argument DeleteStudentDiscussionItemInput input) {
        return service.deleteDiscussionItem(moderator(input.tenantId()), input.targetType(), input.targetId());
    }

    /** Class leaderboard ("Xếp hạng") of the student's own class; no entries once the teacher switched it off. */
    @SchemaMapping(typeName = "ElearningQuery", field = "studentClassLeaderboard")
    @ElearningDashboardRead
    public ClassLeaderboardResponse studentClassLeaderboard(@Argument StudentClassLeaderboardInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        StudentClassLeaderboardInput safe = input == null ? new StudentClassLeaderboardInput(null, null) : input;
        return service.studentClassLeaderboard(
                accessSupport.verifiedTenantId(principal, safe.tenantId()), principal.userId(), safe.classId());
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "leaderboardClasses")
    @ElearningLeaderboardManage
    public List<LeaderboardClassResponse> leaderboardClasses(@Argument String tenantId) {
        return service.leaderboardClasses(manager(tenantId));
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "classLeaderboard")
    @ElearningLeaderboardManage
    public ClassLeaderboardResponse classLeaderboard(@Argument ClassLeaderboardInput input) {
        return service.classLeaderboard(manager(input.tenantId()), input.classId());
    }

    @MutationMapping
    @ElearningLeaderboardManage
    public LeaderboardClassResponse setClassLeaderboardEnabled(@Argument SetClassLeaderboardEnabledInput input) {
        return service.setClassLeaderboardEnabled(manager(input.tenantId()), input.classId(),
                Boolean.TRUE.equals(input.enabled()));
    }

    private DiscussionModerator moderator(String requestedTenantId) {
        return scope(requestedTenantId, "elearning.discussion.moderate");
    }

    private LeaderboardManager manager(String requestedTenantId) {
        DiscussionModerator scope = scope(requestedTenantId, "elearning.leaderboard.manage");
        return new LeaderboardManager(scope.tenantId(), scope.userId(), scope.tenantWide());
    }

    /** Tenant-wide admins (ADMIN workspace role or platform-wide principal) open every class; others only classes taught. */
    private DiscussionModerator scope(String requestedTenantId, String permission) {
        ErgPrincipal principal = requirePrincipal(permission);
        boolean tenantWide = accessSupport.isWildcardPrincipal(principal)
                || WorkspaceCapabilityMapper.canonicalWorkspacesFromVerifiedGrants(principal.roles(), List.of(), List.of())
                        .contains("ADMIN");
        return new DiscussionModerator(accessSupport.verifiedTenantId(principal, requestedTenantId), principal.userId(), tenantWide);
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
    public record StudentDiscussionModerationFeedInput(String tenantId, String classId, Integer page, Integer size) { }
    public record SetStudentDiscussionHiddenInput(String tenantId, String targetType, String targetId, Boolean hidden) { }
    public record DeleteStudentDiscussionItemInput(String tenantId, String targetType, String targetId) { }
    public record StudentClassLeaderboardInput(String tenantId, String classId) { }
    public record ClassLeaderboardInput(String tenantId, String classId) { }
    public record SetClassLeaderboardEnabledInput(String tenantId, String classId, Boolean enabled) { }
}
