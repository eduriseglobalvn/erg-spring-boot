package com.erg.ergspringboot.audit.capture;

import com.erg.ergspringboot.audit.AuditOutcome;
import java.util.List;
import java.util.Set;
import org.springframework.util.AntPathMatcher;

/**
 * What the mutation capture skips. Both lists are explicit constants on purpose: a new write endpoint
 * is audited by default and has to be added here deliberately to opt out ({@code AuditCoverageTest}).
 */
public final class AuditCaptureRules {

    public enum Decision { CAPTURE, SELF_SERVICE, ALREADY_AUDITED, READ_ONLY }

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** Self-service writes (own account, quiz attempts, heartbeats): own data already, would flood the log. */
    public static final List<String> SELF_SERVICE_REST = List.of(
            "/api/v1/users/me/**",
            "/api/v1/users/onboarding",
            "/api/v1/notifications/read-all",
            "/api/v1/notifications/*/read",
            "/api/v1/notifications/*/ack",
            "/api/v1/notifications/preferences",
            "/api/v1/notification-devices/**",
            "/api/v1/lms/attempts",
            "/api/v1/lms/attempts/*/draft",
            "/api/v1/lms/attempts/*/answers",
            "/api/v1/lms/attempts/*/answers/*",
            "/api/v1/lms/attempts/*/sync",
            "/api/v1/lms/attempts/*/submit");

    /** POST only because the query is a body (X-003): computes or previews, changes nothing. */
    public static final List<String> READ_ONLY_REST = List.of(
            "/api/v1/notification-campaigns/estimate",
            "/api/v1/notification-campaigns/people/resolve",
            "/api/v1/calendar/preview",
            "/api/v1/users/*/preview",
            "/api/v1/lcms/quizzes/*/validate");

    /** Endpoints whose use case already writes a detailed audit record (AuthorizationMutationAuditService). */
    public static final List<String> ALREADY_AUDITED_REST = List.of(
            "/api/v1/users/*/roles/*",
            "/api/v1/users/roles/bulk",
            "/api/v1/users/*/role-assignments",
            "/api/v1/users/*/role-assignments/*");

    public static final Set<String> SELF_SERVICE_MUTATIONS = Set.of(
            "createStudentDiscussion", "replyStudentDiscussion", "followStudentDiscussion",
            "askTeacherAboutQuestion", "sendQuestionThreadMessage", "markStudentQuestionThreadRead",
            "setStudentDiscussionReaction", "markStudentAnnouncementRead");

    /** Lesson log (V154 lesson_log_audit) and schedule slot changes (ScheduleSlotChangeAudit) have their own trail. */
    public static final Set<String> ALREADY_AUDITED_MUTATIONS = Set.of(
            "updateLessonLogEntry", "confirmLessonLogWeek", "saveAttendanceAndLog",
            "editTeachingScheduleSlot", "swapTeachingScheduleTeacher");

    private AuditCaptureRules() {}

    public static Decision classifyRest(String pathOrPattern) {
        if (matchesAny(SELF_SERVICE_REST, pathOrPattern)) return Decision.SELF_SERVICE;
        if (matchesAny(ALREADY_AUDITED_REST, pathOrPattern)) return Decision.ALREADY_AUDITED;
        if (matchesAny(READ_ONLY_REST, pathOrPattern)) return Decision.READ_ONLY;
        return Decision.CAPTURE;
    }

    public static Decision classifyMutation(String field) {
        if (SELF_SERVICE_MUTATIONS.contains(field)) return Decision.SELF_SERVICE;
        if (ALREADY_AUDITED_MUTATIONS.contains(field)) return Decision.ALREADY_AUDITED;
        return Decision.CAPTURE;
    }

    public static boolean matchesAny(List<String> patterns, String path) {
        return path != null && patterns.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    /** 401 is not recorded: the actor is unknown. Null means "do not record". */
    public static AuditOutcome outcomeOf(int status, Throwable failure) {
        if (status == 401) return null;
        if (status == 403) return AuditOutcome.DENIED;
        return status >= 400 || failure != null ? AuditOutcome.FAILURE : AuditOutcome.SUCCESS;
    }
}
