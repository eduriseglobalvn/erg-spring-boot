package com.erg.ergspringboot.authorization.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single source of the {@code permissions} table: every permission name, its function and its kind.
 * Classification rule: reads and exports are XEM, anything that changes data is SUA. WORKSPACE holds the
 * portal gates, self-service permissions and the learner-only actions, which roles receive explicitly.
 */
public final class PermissionCatalog {

    public record PermissionDef(String name, PermissionFunction function, PermissionKind kind) {
    }

    private static final List<PermissionDef> ALL = build();
    private static final Map<String, PermissionDef> BY_NAME =
            ALL.stream().collect(Collectors.toUnmodifiableMap(PermissionDef::name, Function.identity()));

    private PermissionCatalog() {
    }

    public static List<PermissionDef> all() {
        return ALL;
    }

    public static Optional<PermissionKind> kindOf(String name) {
        return Optional.ofNullable(BY_NAME.get(name)).map(PermissionDef::kind);
    }

    public static Optional<PermissionFunction> functionOf(String name) {
        return Optional.ofNullable(BY_NAME.get(name)).map(PermissionDef::function);
    }

    private static List<PermissionDef> build() {
        List<PermissionDef> list = new ArrayList<>();
        add(list, PermissionFunction.SCHOOL_CLASS, PermissionKind.XEM,
                "lms.school.read", "lms.class.read", "lms.scope.read", "lms.unit.read", "lms.student_group.read", "lms.read", "lms.dashboard.read", "lms.report.read", "lms.report.export");
        add(list, PermissionFunction.SCHOOL_CLASS, PermissionKind.SUA,
                "lms.school.create", "lms.school.update", "lms.school.archive", "organization.school.activate", "lms.class.manage", "lms.enrollment.manage", "lms.student_group.create", "lms.student_group.update", "lms.student_group.delete", "lms.manage");
        add(list, PermissionFunction.STUDENT, PermissionKind.XEM,
                "lms.student.read", "lms.progress.read");
        add(list, PermissionFunction.STUDENT, PermissionKind.SUA,
                "lms.student.manage");
        add(list, PermissionFunction.SCHEDULE, PermissionKind.XEM,
                "lms.schedule.read", "lms.schedule.read_self");
        add(list, PermissionFunction.SCHEDULE, PermissionKind.SUA,
                "lms.schedule.create", "lms.schedule.update", "lms.schedule.delete", "lms.schedule.import", "lms.schedule.publish", "lms.schedule.manage");
        add(list, PermissionFunction.ATTENDANCE_LOG, PermissionKind.XEM,
                "lms.attendance.read", "lms.attendance.export", "lms.class_log.read");
        add(list, PermissionFunction.ATTENDANCE_LOG, PermissionKind.SUA,
                "lms.attendance.write", "lms.class_log.write");
        add(list, PermissionFunction.GRADES_ASSIGNMENTS, PermissionKind.XEM,
                "lms.grade.read", "lms.grade.export", "lms.assignment.read", "lms.assignment.progress.read", "lms.assignment.read_self");
        add(list, PermissionFunction.GRADES_ASSIGNMENTS, PermissionKind.SUA,
                "lms.grade.write", "lms.grade.finalize", "lms.attempt.grade", "lms.assignment.assign", "lms.assignment.create", "lms.assignment.update", "lms.assignment.delete");
        add(list, PermissionFunction.MATERIALS_EXAMS, PermissionKind.XEM,
                "lms.exercise.read", "lms.quiz.read", "lms.resource.read", "documents.read", "content.resource.read");
        add(list, PermissionFunction.MATERIALS_EXAMS, PermissionKind.SUA,
                "lms.exercise.create", "lms.exercise.update", "lms.exercise.delete", "lms.exercise.assign", "lms.quiz.create", "lms.quiz.publish", "lms.resource.create", "lms.resource.update", "lms.resource.delete", "lms.resource.share", "documents.manage");
        add(list, PermissionFunction.CURRICULUM, PermissionKind.XEM,
                "curriculum.read", "lms.subject.read");
        add(list, PermissionFunction.CURRICULUM, PermissionKind.SUA,
                "curriculum.manage", "lms.subject.manage");
        add(list, PermissionFunction.ELEARNING, PermissionKind.XEM,
                "elearning.read", "elearning.dashboard.read", "elearning.announcement.read_self", "elearning.assignment.read_self", "learning.outcomes.read", "learning.standards.read");
        add(list, PermissionFunction.ELEARNING, PermissionKind.SUA,
                "elearning.manage", "elearning.discussion.moderate", "elearning.leaderboard.manage", "learning.outcomes.manage", "learning.outcomes.issue_certificate", "learning.standards.manage", "learning.standards.launch", "courses.create", "courses.update", "courses.delete");
        add(list, PermissionFunction.TIMEKEEPING, PermissionKind.XEM,
                "timekeeping.read");
        add(list, PermissionFunction.TIMEKEEPING, PermissionKind.SUA,
                "timekeeping.manage", "timekeeping.approve");
        add(list, PermissionFunction.NOTIFICATIONS, PermissionKind.XEM,
                "notifications.read", "lms.notification.read");
        add(list, PermissionFunction.NOTIFICATIONS, PermissionKind.SUA,
                "notifications.manage", "notification.campaign.send");
        add(list, PermissionFunction.ACCOUNTS_STAFF, PermissionKind.XEM,
                "users.read", "users.activity.read");
        add(list, PermissionFunction.ACCOUNTS_STAFF, PermissionKind.SUA,
                "users.manage");
        add(list, PermissionFunction.AUTHORIZATION, PermissionKind.XEM,
                "rbac.role.read", "rbac.permission.read", "rbac.assignment.read");
        add(list, PermissionFunction.AUTHORIZATION, PermissionKind.SUA,
                "rbac.role.assign");
        add(list, PermissionFunction.CRM, PermissionKind.XEM,
                "crm.analytics.read", "crm.school.read");
        add(list, PermissionFunction.CRM, PermissionKind.SUA,
                "crm.activity.manage", "crm.contact.manage", "crm.deal.manage", "crm.handover.approve", "crm.school.create");
        add(list, PermissionFunction.AUDIT, PermissionKind.XEM,
                "audit.read", "audit.export");
        add(list, PermissionFunction.AUDIT, PermissionKind.SUA,
                "audit.manage");
        add(list, PermissionFunction.WEBSITE_CONTENT, PermissionKind.SUA,
                "cms.categories.create", "cms.categories.update", "cms.categories.delete", "cms.posts.create", "cms.posts.update", "cms.posts.delete", "content.manage", "content.lifecycle.submit", "content.lifecycle.approve", "content.lifecycle.publish", "content.lifecycle.archive", "content.lifecycle.rollback", "seo.manage", "ai-content.use", "media.upload", "media.delete", "crawler.manage", "public-disclosure.manage");
        add(list, PermissionFunction.SYSTEM, PermissionKind.XEM,
                "platform.read", "enterprise.connectors.read");
        add(list, PermissionFunction.SYSTEM, PermissionKind.SUA,
                "operations.manage", "api-keys.manage", "lms.admin", "enterprise.connectors.manage", "enterprise.connectors.sync");
        add(list, PermissionFunction.SYSTEM, PermissionKind.WORKSPACE,
                "*", "admin.workspace.access", "teacher.workspace.access", "student.workspace.access", "crm.workspace.access", "lms.portal.access", "elearning.student.preview");
        add(list, PermissionFunction.ACCOUNTS_STAFF, PermissionKind.WORKSPACE,
                "account.self.read", "account.self.update", "account.self.password.change", "account.self.recovery_email.update", "account.self.sessions.read", "profile.self");
        add(list, PermissionFunction.TIMEKEEPING, PermissionKind.WORKSPACE,
                "timekeeping.self");
        add(list, PermissionFunction.NOTIFICATIONS, PermissionKind.WORKSPACE,
                "lms.notification.preferences.update");
        add(list, PermissionFunction.ELEARNING, PermissionKind.WORKSPACE,
                "elearning.discussion.write_self", "lms.assignment.submit", "lms.quiz.play");
        return List.copyOf(list);
    }

    private static void add(List<PermissionDef> list, PermissionFunction function, PermissionKind kind, String... names) {
        for (String name : names) {
            list.add(new PermissionDef(name, function, kind));
        }
    }
}
