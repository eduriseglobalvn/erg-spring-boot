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
                .target(MigrationVersion.fromVersion("75"))
                .load()
                .migrate();

        Flyway current = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
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
