package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorizationVersionMigrationContractTest {

    @Test
    void v61AddsMonotonicAuthorizationVersionToUsers() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/postgres/V61__authorization_version_events.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration).toLowerCase();
        assertThat(sql).contains("alter table users")
                .contains("authz_version bigint not null default 1");
    }
}
