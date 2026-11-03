package com.erg.ergspringboot.audit;

import com.erg.ergspringboot.config.persistence.FlywayConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** C11 (V121): legal hold is settable; retention deletes only expired, un-held rows; nothing else mutates. */
@Testcontainers(disabledWithoutDocker = true)
class AuditLegalHoldTriggerIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_audit")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
                .configuration(FlywayConfig.SESSION_LEVEL_LOCK)
                .load()
                .migrate();
    }

    @Test
    void legalHoldCanBeToggledButOtherColumnsStayImmutable() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            insert(statement, "hold-1", "now() + interval '1 day'");

            assertThat(statement.executeUpdate(
                    "update audit_events set legal_hold = true, updated_at = now() where id = 'hold-1'")).isEqualTo(1);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "update audit_events set action = 'TAMPERED' where id = 'hold-1'"))
                    .hasMessageContaining("audit_events_append_only");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "update audit_events set legal_hold = false, details = '{\"x\":1}'::jsonb where id = 'hold-1'"))
                    .hasMessageContaining("audit_events_append_only");
        }
    }

    @Test
    void retentionDeletesOnlyExpiredRowsThatAreNotOnHold() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            insert(statement, "expired-free", "now() - interval '1 day'");
            insert(statement, "expired-held", "now() - interval '1 day'");
            insert(statement, "fresh", "now() + interval '30 days'");
            statement.executeUpdate("update audit_events set legal_hold = true where id = 'expired-held'");
            connection.setAutoCommit(false);

            statement.execute("select set_config('app.audit_retention_job','true',true)");
            assertThat(statement.executeUpdate("delete from audit_events where id = 'expired-free'")).isEqualTo(1);
            connection.commit();

            statement.execute("select set_config('app.audit_retention_job','true',true)");
            assertThatThrownBy(() -> statement.executeUpdate("delete from audit_events where id = 'expired-held'"))
                    .hasMessageContaining("audit_events_append_only");
            connection.rollback();

            statement.execute("select set_config('app.audit_retention_job','true',true)");
            assertThatThrownBy(() -> statement.executeUpdate("delete from audit_events where id = 'fresh'"))
                    .hasMessageContaining("audit_events_append_only");
            connection.rollback();
        }
    }

    private void insert(Statement statement, String id, String retentionUntil) throws Exception {
        statement.executeUpdate("""
                insert into audit_events (id, tenant_id, action, resource_type, outcome, idempotency_key, event_hash,
                    occurred_at, retention_until)
                values ('%s', 'tenant-a', 'TEST', 'TEST', 'SUCCESS', '%s', repeat('a', 64), now(), %s)
                """.formatted(id, id, retentionUntil));
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
