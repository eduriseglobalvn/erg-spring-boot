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
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyFramesResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.QuestionThreadResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.MyTierResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.TierTableRowResponse;
import com.erg.ergspringboot.elearning.ElearningOperations;
import com.erg.ergspringboot.elearning.StudentQuestionThreadOperations;
import com.erg.ergspringboot.elearning.StudentTierOperations;
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
import com.erg.ergspringboot.exception.ForbiddenException;
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
    private final StudentTierOperations tiers;
    private final StudentQuestionThreadOperations questionThreads;
    private final GraphQlAccessSupport accessSupport;

    public StudentEngagementGraphQlController(ElearningOperations service, StudentTierOperations tiers,
                                              StudentQuestionThreadOperations questionThreads, GraphQlAccessSupport accessSupport) {
        this.service = service;
        this.tiers = tiers;
        this.questionThreads = questionThreads;
        this.accessSupport = accessSupport;
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "discussionFeed")
    @ElearningDashboardRead
    public StudentDiscussionFeedResponse discussionFeed(@Argument StudentDiscussionFeedInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        StudentDiscussionFeedInput safe = input == null ? new StudentDiscussionFeedInput(null, 0, 20, null, null) : input;
        return service.discussionFeed(new DiscussionFeedQuery(
                accessSupport.verifiedTenantId(principal, safe.tenantId()), principal.userId(),
                safe.page() == null ? 0 : safe.page(), safe.size() == null ? 20 : safe.size(), safe.filter(), safe.subject()
        ));
    }

    @MutationMapping
    @ElearningDiscussionWriteSelf
    public StudentDiscussionPostResponse createStudentDiscussion(@Argument CreateStudentDiscussionInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.discussion.write_self");
        return service.createDiscussion(new CreateDiscussionCommand(
                accessSupport.verifiedTenantId(principal, input.tenantId()), principal.userId(), input.idempotencyKey(),
                input.title(), input.content(), input.assignmentId(), safe(input.attachmentIds()), input.subject()
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
    public boolean followStudentDiscussion(@Argument FollowStudentDiscussionInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.discussion.write_self");
        return service.followDiscussion(accessSupport.verifiedTenantId(principal, input.tenantId()), principal.userId(),
                input.postId(), Boolean.TRUE.equals(input.follow()));
    }

    @MutationMapping
    @ElearningDiscussionModerate
    public StudentDiscussionPostResponse setStudentDiscussionPinned(@Argument SetStudentDiscussionPinnedInput input) {
        return service.setDiscussionPinned(moderator(input.tenantId()), input.postId(), Boolean.TRUE.equals(input.pinned()));
    }

    /** A null commentId clears the chosen answer. */
    @MutationMapping
    @ElearningDiscussionModerate
    public StudentDiscussionPostResponse acceptStudentDiscussionReply(@Argument AcceptStudentDiscussionReplyInput input) {
        return service.acceptDiscussionReply(moderator(input.tenantId()), input.postId(), input.commentId());
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "myQuestionThreads")
    @ElearningDashboardRead
    public List<QuestionThreadResponse> myQuestionThreads(@Argument String status, @Argument String tenantId) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        return questionThreads.list(accessSupport.verifiedTenantId(principal, tenantId), principal.userId(), false, false, status);
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "questionThread")
    @ElearningDashboardRead
    public QuestionThreadResponse questionThread(@Argument String id, @Argument String tenantId) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        return questionThreads.detail(accessSupport.verifiedTenantId(principal, tenantId), principal.userId(), false, id);
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "teacherQuestionThreads")
    @ElearningDiscussionModerate
    public List<QuestionThreadResponse> teacherQuestionThreads(@Argument String status, @Argument String tenantId) {
        DiscussionModerator teacher = moderator(tenantId);
        return questionThreads.list(teacher.tenantId(), teacher.userId(), teacher.tenantWide(), true, status);
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "teacherQuestionThread")
    @ElearningDiscussionModerate
    public QuestionThreadResponse teacherQuestionThread(@Argument String id, @Argument String tenantId) {
        DiscussionModerator teacher = moderator(tenantId);
        return questionThreads.detail(teacher.tenantId(), teacher.userId(), teacher.tenantWide(), id);
    }

    @MutationMapping
    @ElearningDiscussionWriteSelf
    public QuestionThreadResponse askTeacherAboutQuestion(@Argument AskTeacherAboutQuestionInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.discussion.write_self");
        return questionThreads.ask(accessSupport.verifiedTenantId(principal, input.tenantId()), principal.userId(),
                input.idempotencyKey(), input.assignmentId(), input.questionIndex() == null ? 0 : input.questionIndex(),
                input.questionPrompt(), input.studentAnswerText(), input.message(), safe(input.attachmentIds()));
    }

    @MutationMapping
    @ElearningDiscussionWriteSelf
    public QuestionThreadResponse sendQuestionThreadMessage(@Argument SendQuestionThreadMessageInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.discussion.write_self");
        return questionThreads.send(accessSupport.verifiedTenantId(principal, input.tenantId()), principal.userId(), false,
                input.threadId(), input.message(), safe(input.attachmentIds()));
    }

    @MutationMapping
    @ElearningDashboardRead
    public boolean markStudentQuestionThreadRead(@Argument String threadId, @Argument String tenantId) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        return questionThreads.markRead(accessSupport.verifiedTenantId(principal, tenantId), principal.userId(), threadId);
    }

    @MutationMapping
    @ElearningDiscussionModerate
    public QuestionThreadResponse answerQuestionThread(@Argument SendQuestionThreadMessageInput input) {
        DiscussionModerator teacher = moderator(input.tenantId());
        return questionThreads.send(teacher.tenantId(), teacher.userId(), teacher.tenantWide(), input.threadId(), input.message(),
                safe(input.attachmentIds()));
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

    /** "Thi đua" board of the student's own class or grade, by XP; no entries once the teacher switched it off. */
    @SchemaMapping(typeName = "ElearningQuery", field = "studentClassLeaderboard")
    @ElearningDashboardRead
    public ClassLeaderboardResponse studentClassLeaderboard(@Argument StudentClassLeaderboardInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        StudentClassLeaderboardInput safe = input == null ? new StudentClassLeaderboardInput(null, null, null, null) : input;
        return service.studentClassLeaderboard(
                accessSupport.verifiedTenantId(principal, safe.tenantId()), principal.userId(), safe.classId(),
                safe.period(), safe.scope());
    }

    /** The student hides themselves from classmates on the board; they still see their own rank. */
    @MutationMapping
    @ElearningDashboardRead
    public boolean setMyLeaderboardHidden(@Argument SetMyLeaderboardHiddenInput input) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        return service.setMyLeaderboardHidden(accessSupport.verifiedTenantId(principal, input.tenantId()),
                principal.userId(), Boolean.TRUE.equals(input.hidden()));
    }

    /** Tier of the authenticated student; the tenant comes from the principal. */
    @SchemaMapping(typeName = "ElearningQuery", field = "myTier")
    @ElearningDashboardRead
    public MyTierResponse myTier(@Argument String tenantId) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        return tiers.myTier(accessSupport.verifiedTenantId(principal, tenantId), principal.userId());
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "tierTable")
    @ElearningDashboardRead
    public List<TierTableRowResponse> tierTable(@Argument String weekStart, @Argument String tenantId) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        return tiers.tierTable(accessSupport.verifiedTenantId(principal, tenantId), principal.userId(), weekStart);
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "myFrames")
    @ElearningDashboardRead
    public MyFramesResponse myFrames(@Argument String tenantId) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        return tiers.myFrames(accessSupport.verifiedTenantId(principal, tenantId), principal.userId());
    }

    @MutationMapping
    @ElearningDashboardRead
    public MyFramesResponse selectFrame(@Argument String tierKey, @Argument String tenantId) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        return tiers.selectFrame(accessSupport.verifiedTenantId(principal, tenantId), principal.userId(), tierKey);
    }

    @MutationMapping
    @ElearningDashboardRead
    public boolean acknowledgeTierPromotion(@Argument String weekStart, @Argument String tenantId) {
        ErgPrincipal principal = requirePrincipal("elearning.dashboard.read");
        return tiers.acknowledgeTierPromotion(accessSupport.verifiedTenantId(principal, tenantId), principal.userId(), weekStart);
    }

    /** Same guard as the leaderboard admin screens, narrowed to tenant-wide admins; runs for the caller's tenant only. */
    @MutationMapping
    @ElearningLeaderboardManage
    public int runStudentTierUpdate(@Argument String tenantId) {
        LeaderboardManager admin = manager(tenantId);
        if (!admin.tenantWide()) throw new ForbiddenException("Running the tier update requires a tenant-wide admin.");
        return tiers.runTierUpdate(admin.tenantId());
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "leaderboardClasses")
    @ElearningLeaderboardManage
    public List<LeaderboardClassResponse> leaderboardClasses(@Argument String tenantId) {
        return service.leaderboardClasses(manager(tenantId));
    }

    @SchemaMapping(typeName = "ElearningQuery", field = "classLeaderboard")
    @ElearningLeaderboardManage
    public ClassLeaderboardResponse classLeaderboard(@Argument ClassLeaderboardInput input) {
        return service.classLeaderboard(manager(input.tenantId()), input.classId(), input.period());
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

    public record StudentDiscussionFeedInput(String tenantId, Integer page, Integer size, String filter, String subject) { }

    public record CreateStudentDiscussionInput(String tenantId, String idempotencyKey, String title, String content,
                                                String assignmentId, List<String> attachmentIds, String subject) { }
    public record FollowStudentDiscussionInput(String tenantId, String postId, Boolean follow) { }
    public record SetStudentDiscussionPinnedInput(String tenantId, String postId, Boolean pinned) { }
    public record AcceptStudentDiscussionReplyInput(String tenantId, String postId, String commentId) { }
    public record AskTeacherAboutQuestionInput(String tenantId, String idempotencyKey, String assignmentId, Integer questionIndex,
                                               String questionPrompt, String studentAnswerText, String message,
                                               List<String> attachmentIds) { }
    public record SendQuestionThreadMessageInput(String tenantId, String threadId, String message, List<String> attachmentIds) { }
    public record ReplyStudentDiscussionInput(String tenantId, String idempotencyKey, String postId,
                                               String parentCommentId, String content, List<String> attachmentIds) { }
    public record SetStudentDiscussionReactionInput(String tenantId, String targetType, String targetId, String reaction) { }
    public record MarkStudentAnnouncementReadInput(String tenantId, String announcementId) { }
    public record StudentDiscussionModerationFeedInput(String tenantId, String classId, Integer page, Integer size) { }
    public record SetStudentDiscussionHiddenInput(String tenantId, String targetType, String targetId, Boolean hidden) { }
    public record DeleteStudentDiscussionItemInput(String tenantId, String targetType, String targetId) { }
    public record StudentClassLeaderboardInput(String tenantId, String classId, String period, String scope) { }
    public record ClassLeaderboardInput(String tenantId, String classId, String period) { }
    public record SetMyLeaderboardHiddenInput(String tenantId, Boolean hidden) { }
    public record SetClassLeaderboardEnabledInput(String tenantId, String classId, Boolean enabled) { }
}
