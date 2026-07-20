package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScopedRoleAssignmentMigrationContractTest {

    @Test
    void scopedAssignmentsNormalizeTenantScopeExpiryActorAndRevocation() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V59__scoped_role_assignments.sql"));
        assertThat(sql).contains(
                "create table if not exists user_role_assignments",
                "user_id",
                "role_id",
                "tenant_id",
                "scope_type",
                "scope_id",
                "expires_at",
                "assigned_by",
                "revoked_at",
                "revoked_by"
        );
        assertThat(sql).doesNotContain("modules varchar", "modules text");
        assertThat(sql).contains("where revoked_at is null");
    }

    @Test
    void roleSeedsHandleLegacyIdsAndCanonicalNamesWithoutPrimaryKeyCollisions() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V59__scoped_role_assignments.sql"))
                .toLowerCase();

        assertThat(sql).contains(
                "update roles",
                "set name = 'center_manager'",
                "where id = 'role_center_manager'",
                "where not exists (select 1 from roles where id = 'role_center_manager' or lower(name) = 'center_manager')"
        );
    }
}
