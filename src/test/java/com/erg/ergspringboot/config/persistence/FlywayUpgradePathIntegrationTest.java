package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class FlywayUpgradePathIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_upgrade")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    @Test
    void previousReleaseDatabaseUpgradesToCurrentWithoutRebuild() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
                .configuration(FlywayConfig.SESSION_LEVEL_LOCK)
                .target(MigrationVersion.fromVersion("75"))
                .load()
                .migrate();

        Flyway current = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
                .configuration(FlywayConfig.SESSION_LEVEL_LOCK)
                .load();
        var result = current.migrate();

        assertThat(result.success).isTrue();
            // Flyway 11 exposes targetSchemaVersion as a quoted value while MigrationVersion
            // exposes the unquoted version; compare the normalized textual value.
            assertThat(String.valueOf(result.targetSchemaVersion).replace("\"", ""))
                .isEqualTo(current.info().current().getVersion().toString());
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            assertThat(indexExists(statement, "uq_lms_assignments_tenant_idempotency")).isTrue();
            assertThat(indexExists(statement, "uq_quiz_versions_publish_idempotency")).isTrue();
            assertThat(indexExists(statement, "uq_crm_deals_owner_idempotency")).isTrue();
            assertThat(indexExists(statement, "uq_crm_handovers_tenant_actor_idempotency")).isTrue();
            assertThat(indexExists(statement, "uq_student_discussion_thread_idempotency")).isTrue();
            assertThat(indexExists(statement, "uq_student_announcement_read_actor")).isTrue();
            assertThat(indexExists(statement, "uq_lms_attendance_natural")).isTrue();
            assertThat(indexExists(statement, "idx_lms_gradebook_workspace")).isTrue();

            // V114: online/hot-path and user-FK indexes are built (CONCURRENTLY) and valid.
            for (String index : new String[] {
                    "idx_assignment_recipients_tenant_user_assignment", "idx_user_activities_tenant_created_id",
                    "idx_lms_attempts_tenant_version_status_id", "idx_notification_recipients_user_id",
                    "idx_student_progress_student_user_id"}) {
                assertThat(text(statement, "select coalesce(bool_and(i.indisvalid), false)::text from pg_index i "
                        + "join pg_class c on c.oid = i.indexrelid where c.relname = '" + index + "'"))
                        .as("valid index %s", index).isEqualTo("true");
            }
            // V111: curriculum tables are tenant-isolated with ENABLE + FORCE RLS.
            assertThat(text(statement, "select count(*) from pg_class where relrowsecurity and relforcerowsecurity "
                    + "and relname in ('curriculum_teaching_plan_settings','curriculum_programs',"
                    + "'curriculum_program_rows')")).isEqualTo("3");
            // V112: unused demo seed accounts are INACTIVE; the super admin is untouched.
            assertThat(text(statement, "select count(*) from users where status = 'ACTIVE' and id in "
                    + "('usr_teacher_ba','usr_teacher_cl','usr_stu_ba_0001','usr_stu_cl_0001',"
                    + "'usr_relationship_placeholder','usr_teacher_truc','usr_teacher_vuong','usr_teacher_tuan',"
                    + "'usr_teacher_vang','usr_teacher_tram')")).isEqualTo("0");
            assertThat(text(statement, "select status from users where id = 'usr_super_admin'")).isEqualTo("ACTIVE");
            // V113: activity audit rows survive a user delete.
            assertThat(text(statement, "select is_nullable from information_schema.columns "
                    + "where table_name = 'user_activities' and column_name = 'user_id'")).isEqualTo("YES");
            assertThat(text(statement, "select confdeltype::text from pg_constraint "
                    + "where conname = 'fk_user_activities_user'")).isEqualTo("n");
            // V108: role_id matches roles.id.
            assertThat(text(statement, "select character_maximum_length::text from information_schema.columns "
                    + "where table_name = 'user_role_assignments' and column_name = 'role_id'")).isEqualTo("36");
        }
    }

    private String text(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) {
            assertThat(result.next()).as(sql).isTrue();
            return result.getString(1);
        }
    }

    private boolean indexExists(java.sql.Statement statement, String indexName) throws Exception {
        try (var result = statement.executeQuery(
                "select exists(select 1 from pg_indexes where schemaname='public' and indexname='" + indexName + "')")) {
            result.next();
            return result.getBoolean(1);
        }
    }
}
