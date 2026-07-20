package com.erg.ergspringboot.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TransactionalOutboxMigrationContractTest {
    @Test
    void migrationHasDurableClaimAndIdempotencyFields() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/postgres/V66__transactional_outbox.sql"))
                .toLowerCase();
        assertThat(sql).contains("create table if not exists outbox_events")
                .contains("tenant_id")
                .contains("idempotency_key")
                .contains("lease_until")
                .contains("for outbox_events");
    }
}
