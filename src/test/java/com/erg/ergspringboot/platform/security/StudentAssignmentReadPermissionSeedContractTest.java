package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The /student route needs elearning.assignment.read_self; without a seed learners got 403 on login. */
class StudentAssignmentReadPermissionSeedContractTest {

    private static final String SQL_PATH =
            "src/main/resources/db/migration/postgres/V136__elearning_student_assignment_read_permission.sql";

    @Test
    void v136SeedsThePermissionAndBindsItToTheLearnerRoleOnly() throws Exception {
        String sql = Files.readString(Path.of(SQL_PATH));

        assertThat(sql).contains("'elearning.assignment.read_self'", "ON CONFLICT (name) DO NOTHING",
                "role.id = 'role_lms_student'");
        assertThat(sql).doesNotContain("role_system_admin", "role_lms_teacher");
    }
}
