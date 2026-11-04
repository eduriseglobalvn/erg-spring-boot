package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StudentPreviewPermissionSeedContractTest {

    private static final String SQL_PATH =
            "src/main/resources/db/migration/postgres/V127__elearning_student_preview_permission.sql";

    @Test
    void v127SeedsPreviewPermissionAndGrantsItOnlyToStaffRoles() throws Exception {
        String sql = Files.readString(Path.of(SQL_PATH));

        assertThat(sql).contains("'elearning.student.preview'", "ON CONFLICT (name) DO UPDATE");
        assertThat(sql).contains(
                "'role_lms_teacher_standard'", "'role_lms_homeroom_teacher'", "'role_lms_subject_teacher'",
                "'role_lms_teacher_lead'", "'role_pdt_operator'", "'role_system_admin'");
        assertThat(sql).doesNotContain("role_lms_student", "role_lms_teacher_readonly", "role_lms_teacher_assistant");
    }

    @Test
    void v127GrantsNoLearnerCapability() throws Exception {
        String sql = Files.readString(Path.of(SQL_PATH));

        assertThat(sql).doesNotContain("'lms.quiz.play'", "perm_lms_attempt");
    }
}
