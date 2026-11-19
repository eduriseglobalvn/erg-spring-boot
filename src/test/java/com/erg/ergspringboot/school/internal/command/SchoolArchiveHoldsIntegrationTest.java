package com.erg.ergspringboot.school.internal.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.config.persistence.FlywayConfig;
import com.erg.ergspringboot.users.persistence.UserActivityRepository;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Archiving a school locks its open classes, students and their logins; restore reopens only those. */
@Testcontainers(disabledWithoutDocker = true)
class SchoolArchiveHoldsIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_school_archive")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    @Test
    void archiveLocksOnlyOpenRowsAndRestoreReopensExactlyThose() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres").configuration(FlywayConfig.SESSION_LEVEL_LOCK)
                .load().migrate();
        var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.queryForObject("select set_config('app.maintenance_mode', 'true', false)", String.class);
        seed(jdbc);
        UserActivityRepository activities = mock(UserActivityRepository.class);
        SchoolArchiveHolds holds = new SchoolArchiveHolds(jdbc, activities);

        SchoolArchiveHolds.Counts archived = holds.archive("erg", "sch_t", "usr_admin");

        assertThat(archived).isEqualTo(new SchoolArchiveHolds.Counts(1, 3, 1));
        assertThat(status(jdbc, "academic_classes", "c_open")).isEqualTo("ARCHIVED");
        assertThat(status(jdbc, "lms_students", "st_a")).isEqualTo("ARCHIVED");
        assertThat(status(jdbc, "users", "u_a")).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject("select revoked_reason from user_sessions where id = 'ses_a'", String.class))
                .isEqualTo("status_changed_inactive");
        assertThat(status(jdbc, "users", "u_b")).as("student archived on its own earlier").isEqualTo("ACTIVE");
        assertThat(status(jdbc, "users", "u_d")).as("still studying at another school").isEqualTo("ACTIVE");
        verify(activities).save(argThat(a -> "SCHOOL_ARCHIVED".equals(a.getAction())));

        jdbc.update("update lms_students set status = 'INACTIVE' where id = 'st_c'"); // an admin change after the archive

        SchoolArchiveHolds.Counts restored = holds.restore("erg", "sch_t", "usr_admin");

        assertThat(restored).isEqualTo(new SchoolArchiveHolds.Counts(1, 2, 1));
        assertThat(status(jdbc, "academic_classes", "c_open")).isEqualTo("active");
        assertThat(status(jdbc, "academic_classes", "c_done")).isEqualTo("archived");
        assertThat(status(jdbc, "lms_students", "st_a")).isEqualTo("active");
        assertThat(status(jdbc, "lms_students", "st_b")).isEqualTo("ARCHIVED");
        assertThat(status(jdbc, "lms_students", "st_c")).isEqualTo("INACTIVE");
        assertThat(status(jdbc, "users", "u_a")).isEqualTo("ACTIVE");
        assertThat(status(jdbc, "users", "u_c")).isEqualTo("BLOCKED");
        assertThat(jdbc.queryForObject("select count(*) from school_archive_holds where school_id = 'sch_t'", Integer.class)).isZero();
        verify(activities).save(argThat(a -> "SCHOOL_RESTORED".equals(a.getAction())));
        connection.close();
    }

    private static String status(JdbcTemplate jdbc, String table, String id) {
        return jdbc.queryForObject("select status from " + table + " where id = ?", String.class, id);
    }

    private static void seed(JdbcTemplate jdbc) {
        for (String[] user : new String[][] {{"u_a", "ACTIVE"}, {"u_b", "ACTIVE"}, {"u_c", "BLOCKED"}, {"u_d", "ACTIVE"}}) {
            jdbc.update("insert into users (id, created_at, updated_at, email, full_name, status, provider, account_type,"
                    + " tenant_id, is_profile_completed, must_change_password, recovery_email_verified, login_count)"
                    + " values (?, now(), now(), ? || '@example.test', 'Student', ?, 'local', 'erg', 'erg', true, false, true, 0)",
                    user[0], user[0], user[1]);
        }
        jdbc.update("insert into education_units (id, created_at, updated_at, tenant_id, name)"
                + " values ('sch_t', now(), now(), 'erg', 'T School'), ('sch_o', now(), now(), 'erg', 'O School')");
        jdbc.update("insert into user_sessions (id, created_at, updated_at, user_id, tenant_id) values ('ses_a', now(), now(), 'u_a', 'erg')");
        for (String[] cls : new String[][] {{"c_open", "active"}, {"c_done", "archived"}}) {
            jdbc.update("insert into academic_classes (id, created_at, updated_at, tenant_id, school_id, name, grade, academic_year, status)"
                    + " values (?, now(), now(), 'erg', 'sch_t', ?, 'grade-6', '2025-2026', ?)", cls[0], cls[0], cls[1]);
        }
        for (String[] stu : new String[][] {
                {"st_a", "sch_t", "u_a", "active"}, {"st_b", "sch_t", "u_b", "ARCHIVED"}, {"st_c", "sch_t", "u_c", "ACTIVE"},
                {"st_d", "sch_t", "u_d", "ACTIVE"}, {"st_d2", "sch_o", "u_d", "ACTIVE"}}) {
            jdbc.update("insert into lms_students (id, created_at, updated_at, tenant_id, school_id, student_code, full_name,"
                    + " username, auth_user_id, status) values (?, now(), now(), 'erg', ?, ?, 'Student', ?, ?, ?)",
                    stu[0], stu[1], stu[0], stu[0], stu[2], stu[3]);
        }
    }
}
