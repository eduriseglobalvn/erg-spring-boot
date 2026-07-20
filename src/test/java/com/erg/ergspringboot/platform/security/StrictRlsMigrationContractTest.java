package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StrictRlsMigrationContractTest {
    @Test
    void strictMigrationDoesNotAllowUnsetTenantGuc() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/postgres/V65__strict_request_rls_roles.sql"))
                .toLowerCase();
        assertThat(sql).contains("force row level security")
                .contains("app.maintenance_mode")
                .contains("nullif(current_setting('app.tenant_id', true), '') is not null")
                .doesNotContain("current_setting('app.tenant_id', true) is null");
    }
}
