package com.erg.ergspringboot.service.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacyRbacIdRepairMigrationContractTest {

    @Test
    void legacyUuidRbacKeysAreRepairedToTheCanonicalStringIdContract() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/postgres/V63__repair_legacy_uuid_rbac_ids.sql");

        assertThat(Files.readString(migration))
                .contains("roles", "permissions", "permission_groups", "role_permissions", "user_roles")
                .contains("permission_bindings", "user_role_assignments")
                .contains("USING %I::text")
                .contains("information_schema.columns");
    }
}
