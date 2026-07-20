package com.erg.ergspringboot.service.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReportJobMigrationContractTest {
    @Test
    void migrationCreatesBoundedTenantScopedLeasedReportJobs() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V67__report_aggregate_tables.sql")).toLowerCase();
        assertThat(sql).contains("create table if not exists report_jobs")
                .contains("tenant_id")
                .contains("idempotency_key")
                .contains("lease_until")
                .contains("force row level security")
                .contains("report_jobs_tenant_isolation");
    }

    @Test
    void migrationCreatesIncrementalTenantScopedAssignmentAggregateAndReconciliationQueue() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V69__assignment_report_aggregate.sql")).toLowerCase();
        assertThat(sql).contains(
                "create table if not exists assignment_progress_aggregates",
                "primary key (tenant_id, assignment_id, shard_id)",
                "hashtextextended",
                "source_version",
                "source_updated_at",
                "create table if not exists report_reconciliation_queue",
                "create trigger trg_student_progress_assignment_aggregate",
                "force row level security",
                "assignment_progress_aggregates_tenant_isolation");
    }
}
