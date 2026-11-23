package com.erg.ergspringboot.authorization.catalog;

import com.erg.ergspringboot.authorization.catalog.PermissionCatalog.PermissionDef;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** The seven business roles and the rule that gives each its permissions. */
public final class BusinessRoleCatalog {

    public static final String SUPER_ADMIN = "role_super_admin";
    public static final String DIRECTOR = "role_director";
    public static final String PDT = "role_pdt_operator";
    public static final String OPERATIONS = "role_operations";
    public static final String TEACHER = "role_lms_teacher";
    public static final String STUDENT = "role_lms_student";
    public static final String ADMISSIONS = "role_admissions";

    private static final Set<String> SELF_SERVICE = Set.of(
            "account.self.read", "account.self.update", "account.self.password.change",
            "account.self.recovery_email.update", "account.self.sessions.read", "profile.self");

    // Union of the former lms.instructor and lms_subject_teacher roles, plus the teacher gate.
    private static final Set<String> TEACHER_PERMISSIONS = Set.of(
            "teacher.workspace.access", "profile.self",
            "account.self.read", "account.self.update", "account.self.password.change",
            "account.self.recovery_email.update", "account.self.sessions.read",
            "elearning.discussion.moderate", "elearning.leaderboard.manage", "elearning.student.preview",
            "lms.assignment.assign", "lms.assignment.create", "lms.assignment.progress.read",
            "lms.assignment.read", "lms.assignment.update", "lms.attempt.grade",
            "lms.attendance.read", "lms.attendance.write", "lms.class.read",
            "lms.class_log.read", "lms.class_log.write", "lms.dashboard.read",
            "lms.exercise.create", "lms.exercise.read", "lms.quiz.read", "learning.outcomes.read", "lms.exercise.update", "lms.grade.read", "lms.grade.write",
            "lms.games.host",
            "lms.notification.preferences.update", "lms.notification.read", "lms.portal.access",
            "notification.campaign.send",
            "lms.report.read", "lms.resource.create", "lms.resource.read", "lms.resource.share",
            "lms.resource.update", "lms.schedule.create", "lms.schedule.read", "lms.schedule.read_self",
            "lms.schedule.update", "lms.student.read", "lms.student_group.create",
            "lms.student_group.read", "lms.student_group.update", "lms.unit.read", "timekeeping.self");

    private static final Set<String> STUDENT_PERMISSIONS = Set.of(
            "student.workspace.access",
            "elearning.announcement.read_self", "elearning.assignment.read_self", "elearning.dashboard.read",
            "elearning.discussion.write_self", "lms.assignment.read_self", "lms.assignment.submit",
            "lms.games.play", "lms.quiz.play", "lms.schedule.read_self", "learning.outcomes.read");

    private static final Set<PermissionFunction> NOT_LMS = EnumSet.of(
            PermissionFunction.CRM, PermissionFunction.AUDIT, PermissionFunction.AUTHORIZATION,
            PermissionFunction.WEBSITE_CONTENT, PermissionFunction.SYSTEM);

    private static final List<BusinessRole> ALL = List.of(
            new BusinessRole(SUPER_ADMIN, "system.super_admin", "Quản trị",
                    "Quản trị toàn bộ hệ thống, kể cả CRM, nhật ký và phân quyền.",
                    List.of("Làm mọi thao tác", "Xem nhật ký hệ thống", "Phân quyền tài khoản"),
                    List.of(), false, Set.of("*")),
            new BusinessRole(DIRECTOR, "director", "Giám đốc",
                    "Xem toàn bộ hệ thống, kể cả CRM và nhật ký, nhưng không được thêm, sửa hay xoá.",
                    List.of("Xem mọi chức năng", "Xem CRM", "Xem nhật ký hệ thống", "Xem phân quyền"),
                    List.of("Thêm, sửa, xoá bất kỳ dữ liệu nào", "Mang thêm vai trò khác"), true,
                    union(select(d -> d.kind() == PermissionKind.XEM),
                            Set.of("admin.workspace.access", "crm.workspace.access"), SELF_SERVICE)),
            new BusinessRole(PDT, "pdt_operator", "Phòng đào tạo",
                    "Toàn quyền vận hành đào tạo và quản lý tài khoản trong phạm vi cho phép, trừ CRM và nhật ký.",
                    List.of("Quản lý trường, lớp, học sinh, lịch dạy, điểm, chương trình, chấm công, thông báo",
                            "Quản lý tài khoản Phòng đào tạo, Vận hành, Giáo viên, Học sinh",
                            "Dùng cổng giáo viên"),
                    List.of("Dùng CRM", "Xem nhật ký hệ thống", "Tác động lên Quản trị, Giám đốc, Tuyển sinh"), false,
                    union(select(d -> d.kind() != PermissionKind.WORKSPACE
                                    && d.function() != PermissionFunction.CRM
                                    && !d.name().startsWith("audit.")
                                    && !d.name().equals("users.activity.read")
                                    && !d.name().equals("api-keys.manage")),
                            Set.of("admin.workspace.access", "teacher.workspace.access", "elearning.student.preview"), SELF_SERVICE,
                            TEACHER_PERMISSIONS)),
            new BusinessRole(OPERATIONS, "operations", "Vận hành",
                    "Chỉ xem phần LMS, không thêm, sửa hay xoá.",
                    List.of("Xem trường, lớp, học sinh, lịch dạy, điểm, chương trình, chấm công, thông báo"),
                    List.of("Thêm, sửa, xoá dữ liệu", "Xem CRM và nhật ký", "Mang thêm vai trò khác"), true,
                    union(select(d -> d.kind() == PermissionKind.XEM && !NOT_LMS.contains(d.function())
                            && !d.name().equals("users.activity.read")),
                            Set.of("admin.workspace.access"), SELF_SERVICE)),
            new BusinessRole(TEACHER, "lms.instructor", "Giáo viên",
                    "Làm việc với lớp mình dạy: lịch dạy, điểm danh, sổ đầu bài, điểm, bài tập, chấm công của mình.",
                    List.of("Xem lịch dạy", "Điểm danh", "Ghi sổ đầu bài", "Nhập điểm và giao bài tập", "Chấm công của mình"),
                    List.of("Quản lý trường, tài khoản, CRM, nhật ký"), false, TEACHER_PERMISSIONS),
            new BusinessRole(STUDENT, "lms.learner", "Học sinh",
                    "Chỉ dùng cổng học sinh.",
                    List.of("Xem lịch học, bài tập, nộp bài, làm quiz, thảo luận"),
                    List.of("Truy cập cổng khác"), false, STUDENT_PERMISSIONS),
            new BusinessRole(ADMISSIONS, "admissions", "Tuyển sinh",
                    "Chỉ dùng CRM.",
                    List.of("Quản lý khách hàng và cơ hội tuyển sinh trong CRM"),
                    List.of("Truy cập LMS, nhật ký, phân quyền"), false,
                    union(select(d -> d.function() == PermissionFunction.CRM && d.kind() != PermissionKind.WORKSPACE),
                            Set.of("crm.workspace.access"), SELF_SERVICE)));

    private BusinessRoleCatalog() {
    }

    public static List<BusinessRole> all() {
        return ALL;
    }

    /** Matrix cell for the admin UI: "SUA", "XEM" or "NONE". */
    public static String cell(BusinessRole role, PermissionFunction function) {
        boolean all = role.permissions().contains("*");
        String best = "NONE";
        for (PermissionDef def : PermissionCatalog.all()) {
            if (def.function() != function || def.kind() == PermissionKind.WORKSPACE) {
                continue;
            }
            if (all || role.permissions().contains(def.name())) {
                if (def.kind() == PermissionKind.SUA) {
                    return "SUA";
                }
                best = "XEM";
            }
        }
        return best;
    }

    private static Set<String> select(Predicate<PermissionDef> filter) {
        Set<String> names = new LinkedHashSet<>();
        for (PermissionDef def : PermissionCatalog.all()) {
            if (filter.test(def)) {
                names.add(def.name());
            }
        }
        return names;
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... parts) {
        Set<String> all = new HashSet<>();
        for (Set<String> part : parts) {
            all.addAll(part);
        }
        return Set.copyOf(all);
    }
}
