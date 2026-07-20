package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeacherLmsPermissionSeedContractTest {

    @Test
    void v52SeedsEveryTeacherPermissionAndAllSixRoleGroups() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V52__lms_teacher_permission_seed.sql"));
        List<String> permissions = List.of(
                "lms.portal.access", "lms.dashboard.read", "lms.class.read", "lms.class.manage",
                "lms.student.read", "lms.student_group.read", "lms.student_group.create",
                "lms.student_group.update", "lms.student_group.delete", "lms.assignment.read",
                "lms.assignment.create", "lms.assignment.update", "lms.assignment.delete",
                "lms.assignment.assign", "lms.assignment.progress.read", "lms.exercise.read",
                "lms.exercise.create", "lms.exercise.update", "lms.exercise.delete", "lms.exercise.assign",
                "lms.grade.read", "lms.grade.write", "lms.grade.finalize", "lms.grade.export",
                "lms.attendance.read", "lms.attendance.write", "lms.attendance.export",
                "lms.schedule.read", "lms.schedule.create", "lms.schedule.update", "lms.schedule.delete",
                "lms.schedule.publish", "lms.schedule.import", "lms.class_log.read", "lms.class_log.write",
                "lms.resource.read", "lms.resource.create", "lms.resource.update", "lms.resource.delete",
                "lms.resource.share", "lms.report.read", "lms.report.export", "lms.notification.read",
                "lms.notification.preferences.update", "account.self.read", "account.self.update",
                "account.self.recovery_email.update", "account.self.password.change", "account.self.sessions.read"
        );
        List<String> roleGroups = List.of(
                "lms_teacher_standard", "lms_homeroom_teacher", "lms_subject_teacher",
                "lms_teacher_assistant", "lms_teacher_readonly", "lms_teacher_lead"
        );

        permissions.forEach(permission -> assertThat(sql).contains("'" + permission + "'"));
        roleGroups.forEach(role -> assertThat(sql).contains("'" + role + "'"));
    }

    @Test
    void v52ResolvesRolePermissionForeignKeysByCanonicalNames() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V52__lms_teacher_permission_seed.sql"));

        assertThat(sql).contains(
                "JOIN lms_teacher_role_seed role_seed",
                "JOIN roles role ON role.name = role_seed.name",
                "JOIN lms_teacher_permission_seed permission_seed",
                "JOIN permissions permission ON permission.name = permission_seed.name"
        );
        assertThat(sql).doesNotContain("AS rp(role_id, permission_id)");
    }
}
