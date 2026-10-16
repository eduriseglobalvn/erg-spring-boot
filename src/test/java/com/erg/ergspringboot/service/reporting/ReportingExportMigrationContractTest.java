package com.erg.ergspringboot.service.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReportingExportMigrationContractTest {
    @Test
    void exportMetadataIsTenantScopedAndFormatBounded() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V91__reporting_export_contract.sql"));
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS format", "ADD COLUMN IF NOT EXISTS expires_at",
                "CHECK (format IN ('json', 'csv'))", "idx_report_jobs_expiry", "expired results");
    }
}
