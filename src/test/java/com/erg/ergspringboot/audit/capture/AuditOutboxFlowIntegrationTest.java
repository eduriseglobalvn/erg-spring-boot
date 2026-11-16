package com.erg.ergspringboot.audit.capture;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Proves the real write path: capture -> audit_outbox -> relay -> AuditOperations.record -> audit_events. */
@ActiveProfiles("test")
@SpringBootTest(properties = "erg.audit.relay.fixed-delay-ms=3600000")
class AuditOutboxFlowIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private AuditOutboxWriter writer;
    @Autowired private AuditOutboxRelay relay;
    @Autowired private JdbcTemplate jdbc;
    @Autowired @Qualifier("postgresTransactionManager") private PlatformTransactionManager transactions;

    @Test
    void capturedActionReachesAuditEventsWithSecretsMasked() {
        String action = "POST /api/v1/it-" + System.nanoTime();
        writer.write(new AuditRecord("erg", "actor-it", "TENANT", "erg", action, "IT", null,
                AuditOutcome.SUCCESS, "req-it", "req-it", null, Map.of("password", "hunter2", "name", "Lop 1A"),
                null, OffsetDateTime.now(), null));

        assertThat(relay.runDueBatch()).isGreaterThanOrEqualTo(1);

        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            Map<String, Object> row = new TransactionTemplate(transactions).execute(status ->
                    jdbc.queryForMap("select actor_id, outcome, details::text as details from audit_events "
                            + "where tenant_id = 'erg' and action = ?", action));
            assertThat(row).containsEntry("actor_id", "actor-it").containsEntry("outcome", "SUCCESS");
            assertThat((String) row.get("details")).contains("[REDACTED]").doesNotContain("hunter2");
        }
    }
}
