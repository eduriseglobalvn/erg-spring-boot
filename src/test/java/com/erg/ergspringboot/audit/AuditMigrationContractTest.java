package com.erg.ergspringboot.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.audit.authorization.AuditExport;
import com.erg.ergspringboot.audit.authorization.AuditRead;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AuditMigrationContractTest {
    @Test
    void migrationPinsTenantIsolationImmutabilityRetentionAndPermissionCatalog() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/postgres/V93__immutable_audit_log.sql"));

        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS audit_events",
                "tenant_id varchar(96) NOT NULL",
                "idempotency_key varchar(192) NOT NULL",
                "event_hash varchar(64) NOT NULL",
                "retention_until timestamptz NOT NULL",
                "legal_hold boolean NOT NULL DEFAULT false",
                "ENABLE ROW LEVEL SECURITY",
                "FORCE ROW LEVEL SECURITY",
                "audit_events_append_only",
                "current_setting('app.audit_retention_job'",
                "'audit.read'",
                "'audit.export'",
                "'audit.manage'");
        assertThat(sql).doesNotContain("DROP TABLE audit_events");
    }

    @Test
    void auditEndpointsUseTheSharedDenyAwareEvaluator() throws Exception {
        for (Class<?> annotationType : new Class<?>[]{AuditRead.class, AuditExport.class}) {
            PreAuthorize authorization = annotationType.getAnnotation(PreAuthorize.class);
            assertThat(authorization).isNotNull();
            assertThat(authorization.value()).contains("@methodAuthorization.isAllowedAny");
            assertThat(authorization.value()).contains(annotationType == AuditRead.class ? "audit.read" : "audit.export");
        }
    }
}
