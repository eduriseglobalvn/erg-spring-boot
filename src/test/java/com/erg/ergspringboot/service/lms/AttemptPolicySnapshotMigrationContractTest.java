package com.erg.ergspringboot.service.lms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AttemptPolicySnapshotMigrationContractTest {

    @Test
    void migrationAddsDurableSnapshotServerConcurrencyAndSubmitRetryColumns() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/postgres/V87__lms_attempt_policy_snapshot_contract.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertThat(sql)
                .contains("add column if not exists policy_snapshot jsonb")
                .contains("add column if not exists submit_idempotency_key varchar(255)")
                .contains("add column if not exists record_version bigint not null default 0")
                .contains("idx_lms_attempts_policy_snapshot");
    }
}
