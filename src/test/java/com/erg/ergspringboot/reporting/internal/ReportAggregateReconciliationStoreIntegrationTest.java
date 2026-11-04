package com.erg.ergspringboot.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** The reconciliation function RETURNS void; the store must call it without reading a value, then drain the queue row. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("postgresAvailable")
class ReportAggregateReconciliationStoreIntegrationTest {
    private static final String TENANT = "tenant-reconcile-it";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    static boolean postgresAvailable() {
        String configured = System.getProperty("erg.test.postgres.url");
        if (configured == null || configured.isBlank()) configured = System.getenv("ERG_TEST_POSTGRES_URL");
        if (configured != null && !configured.isBlank()) return true;
        try { return DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException unavailable) { return false; }
    }

    @Autowired private JdbcClient jdbc;
    @Autowired private PostgresReportAggregateReconciliationStore store;

    @Test
    void reconcileRunsTheVoidFunctionAndDrainsTheQueueRow() {
        jdbc.sql("select set_config('app.tenant_id', :tenant, true)").param("tenant", TENANT).query(row -> { });
        OffsetDateTime source = OffsetDateTime.of(2026, 11, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        jdbc.sql("""
                insert into report_reconciliation_queue (tenant_id, aggregate_type, aggregate_id, source_updated_at)
                values (:tenant, 'assignment_progress', 'asg-reconcile-it', :source)
                """).param("tenant", TENANT).param("source", source).update();

        store.reconcile(new ReportAggregateReconciliationStore.Task(TENANT, "asg-reconcile-it", source));

        assertThat(jdbc.sql("select count(*) from report_reconciliation_queue where tenant_id = :tenant")
                .param("tenant", TENANT).query(Long.class).single()).isZero();
    }
}
