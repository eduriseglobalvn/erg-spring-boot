package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** P3-7: V125 removes the demo seed only on a fresh install that opted in via ERG_FLYWAY_DEMO_PURGE. */
@Testcontainers(disabledWithoutDocker = true)
class DemoSeedPurgeMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_purge")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    private static final String DEMO_ROWS = "select "
            + "(select count(*) from users where id in ('usr_teacher_ba','usr_teacher_cl','usr_stu_ba_0001',"
            + "'usr_stu_cl_0001','usr_teacher_truc','usr_teacher_vuong','usr_teacher_tuan','usr_teacher_vang',"
            + "'usr_teacher_tram','seed.question.bank','seed.quiz.demo'))"
            + " + (select count(*) from education_units where id like 'sch\\_%')"
            + " + (select count(*) from academic_classes)"
            + " + (select count(*) from lms_subjects) + (select count(*) from lms_levels)"
            + " + (select count(*) from lms_quizzes) + (select count(*) from lms_assignments)"
            + " + (select count(*) from lms_students) + (select count(*) from quiz_versions)"
            + " + (select count(*) from quiz_packages) + (select count(*) from teaching_schedule_rooms)"
            + " + (select count(*) from teaching_schedule_events)"
            + " + (select count(*) from teaching_schedule_event_teachers)"
            + " + (select count(*) from curriculum_teaching_plan_settings)"
            + " + (select count(*) from curriculum_subjects where id in ('sub_math','sub_toan'))";

    @Test
    void freshInstallWithFlagRemovesDemoRowsAndKeepsSystemAccounts() throws Exception {
        String url = database("purge_on");
        assertThat(flyway(url, true).migrate().success).isTrue();

        try (Connection connection = connect(url); var statement = connection.createStatement()) {
            assertThat(number(statement, DEMO_ROWS)).isZero();
            assertThat(number(statement, "select count(*) from users where id in "
                    + "('usr_super_admin','usr_relationship_placeholder')")).isEqualTo(2);
            assertThat(number(statement, "select count(*) from user_roles where user_id = 'usr_super_admin'"))
                    .isPositive();
            assertThat(number(statement, "select count(*) from education_units where id = 'ctr_system'")).isOne();
            assertThat(number(statement, "select count(*) from curriculum_subjects where id = 'sub_ic3'")).isOne();
            assertThat(number(statement, "select count(*) from _quarantine_v125_users")).isEqualTo(11);
            assertThat(number(statement, "select count(*) from _quarantine_v125_teaching_schedule_events"))
                    .isEqualTo(120);
        }
        // The flag is a session setting, not a placeholder: the checksum is identical without it.
        assertThat(flyway(url, false).validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void defaultLeavesDemoRowsUntouched() throws Exception {
        String url = database("purge_off");
        assertThat(flyway(url, false).migrate().success).isTrue();

        try (Connection connection = connect(url); var statement = connection.createStatement()) {
            assertThat(number(statement, DEMO_ROWS)).isEqualTo(11 + 4 + 135 + 9 + 4 + 1 + 2 + 2 + 1 + 1 + 7 + 120 + 288 + 1 + 2);
            assertThat(number(statement, "select count(*) from pg_tables where tablename like '\\_quarantine\\_v125%'"))
                    .isZero();
        }
    }

    @Test
    void flagOnADatabaseWithRealUsersFailsWithoutDeleting() throws Exception {
        String url = database("purge_used");
        Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
                .configuration(FlywayConfig.SESSION_LEVEL_LOCK)
                .target(MigrationVersion.fromVersion("124"))
                .load().migrate();
        try (Connection connection = connect(url); var statement = connection.createStatement()) {
            statement.execute("insert into users (id, created_at, updated_at, email, full_name, status, provider, "
                    + "account_type, tenant_id, is_profile_completed, login_count) values ('usr_real', now(), now(), "
                    + "'real@school.test', 'Real User', 'ACTIVE', 'local', 'erg', 'erg', true, 3)");
        }

        assertThatThrownBy(() -> flyway(url, true).migrate()).hasMessageContaining("fresh install");
        try (Connection connection = connect(url); var statement = connection.createStatement()) {
            assertThat(number(statement, "select count(*) from users where id = 'usr_teacher_ba'")).isOne();
            assertThat(number(statement, "select count(*) from flyway_schema_history where version = '125'")).isZero();
        }
    }

    private static Flyway flyway(String url, boolean purge) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(FlywayConfig.DEMO_PURGE, String.valueOf(purge));
        return Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
                .configuration(FlywayConfig.SESSION_LEVEL_LOCK)
                .initSql(FlywayConfig.demoPurgeInitSql(environment))
                .load();
    }

    private static String database(String name) throws Exception {
        try (Connection connection = connect(postgres.getJdbcUrl()); var statement = connection.createStatement()) {
            statement.execute("create database " + name);
        }
        return postgres.getJdbcUrl().replace("/erg_purge", "/" + name);
    }

    private static Connection connect(String url) throws Exception {
        return DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
    }

    private static long number(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) {
            assertThat(result.next()).as(sql).isTrue();
            return result.getLong(1);
        }
    }
}
