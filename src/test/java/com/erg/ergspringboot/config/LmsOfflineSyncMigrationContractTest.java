package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LmsOfflineSyncMigrationContractTest {

    @Test
    void migrationLocksScopedReceiptAndVersionBoundaries() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V89__lms_offline_sync_operation_receipts.sql"));

        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS lms_sync_operations",
                "PRIMARY KEY (tenant_id, account_id, idempotency_key)",
                "UNIQUE (tenant_id, account_id, operation_id)",
                "CHECK (status IN ('PENDING', 'SUCCEEDED'))",
                "ENABLE ROW LEVEL SECURITY",
                "record_version");
        assertThat(sql).contains("30 days");
    }
}
