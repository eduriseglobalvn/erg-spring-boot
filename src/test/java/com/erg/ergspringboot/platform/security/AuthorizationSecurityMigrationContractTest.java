package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorizationSecurityMigrationContractTest {

    @Test
    void migrationAddsAuthorizationUniquenessAndTenantHotPathIndexes() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V56__authorization_security_boundaries.sql"));

        assertThat(sql).contains(
                "ux_roles_name_lower",
                "idx_users_tenant_id_id",
                "idx_user_permission_overrides_user_id",
                "Reserved system role names remain service-owned"
        );
    }
}
