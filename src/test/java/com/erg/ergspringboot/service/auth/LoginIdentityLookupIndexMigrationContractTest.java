package com.erg.ergspringboot.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoginIdentityLookupIndexMigrationContractTest {

    @Test
    void caseInsensitiveLoginLookupHasIndexesForTheLockedIdentityQuery() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/postgres/V64__login_identity_lookup_indexes.sql");

        assertThat(Files.readString(migration))
                .contains("lower(username)")
                .contains("lower(email)")
                .contains("idx_users_lower_username")
                .contains("idx_users_lower_email");
    }
}
