package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminAccountLifecycleBackfillContractTest {

    @Test
    void v55ActivatesOnlyCompletedSystemAdminsWithoutTemporaryPasswords() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V55__backfill_existing_system_admin_lifecycle.sql"));

        assertThat(sql).contains(
                "recovery_email_verified = true",
                "role.name = 'system.super_admin'",
                "users.is_profile_completed = true",
                "users.must_change_password = false"
        );
    }
}
