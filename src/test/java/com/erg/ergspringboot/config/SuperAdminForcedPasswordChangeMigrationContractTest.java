package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SuperAdminForcedPasswordChangeMigrationContractTest {
    private static final Path DIR = Path.of("src/main/resources/db/migration/postgres");
    private static final Pattern HASH = Pattern.compile("'(\\$argon2id\\$[^']+)'");

    @Test
    void forcedChangeTargetsOnlyTheV110TemporaryHash() throws Exception {
        String v110 = Files.readString(DIR.resolve("V110__rotate_super_admin_password.sql"));
        String v126 = Files.readString(DIR.resolve("V126__force_super_admin_password_change.sql"));
        var rotated = HASH.matcher(v110);
        var guarded = HASH.matcher(v126);
        assertThat(rotated.find()).isTrue();
        assertThat(guarded.find()).isTrue();
        // A different hash means an admin who already changed the password is not forced again.
        assertThat(guarded.group(1)).isEqualTo(rotated.group(1));
        assertThat(v126).contains("must_change_password = true").contains("id = 'usr_super_admin'");
    }
}
