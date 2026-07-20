package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PermissionCatalogMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/postgres/V57__permission_catalog_and_bindings.sql"
    );

    @Test
    void migrationCreatesDatabaseBackedPermissionCatalogAndUsageBindings() throws Exception {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains(
                "ADD COLUMN IF NOT EXISTS resource",
                "ADD COLUMN IF NOT EXISTS action",
                "ADD COLUMN IF NOT EXISTS portal",
                "ADD COLUMN IF NOT EXISTS supported_scope",
                "ADD COLUMN IF NOT EXISTS is_system",
                "ADD COLUMN IF NOT EXISTS status",
                "ADD COLUMN IF NOT EXISTS deprecated_at",
                "CREATE TABLE IF NOT EXISTS permission_bindings",
                "surface_type",
                "surface_identifier",
                "REFERENCES permissions(id)",
                "ux_permission_bindings_surface",
                "idx_permission_bindings_permission"
        );
    }
}
