package com.erg.ergspringboot.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserAccountBoundaryTest {

    @Test
    void publicDirectoryPortIsPersistenceNeutral() throws Exception {
        String port = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/users/UserDirectoryPort.java"));
        String projection = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/users/UserAccountProjection.java"));

        assertThat(port)
                .doesNotContain("model.users.User")
                .doesNotContain("UserRepository")
                .doesNotContain("@Entity");
        assertThat(projection)
                .doesNotContain("model.users.User")
                .doesNotContain("UserRepository")
                .doesNotContain("@Entity");
    }

    @Test
    void projectionRejectsUnscopedAccountsAndHasStableDisplayFallbacks() {
        assertThatThrownBy(() -> new UserAccountProjection("u-1", "", "Teacher", null, "ACTIVE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        assertThat(new UserAccountProjection("u-1", "tenant-1", null, "teacher@example.com", "ACTIVE")
                .displayName()).isEqualTo("teacher@example.com");
        assertThat(new UserAccountProjection("u-1", "tenant-1", null, null, "ACTIVE")
                .displayName()).isEqualTo("u-1");
    }

    @Test
    void legacyDirectoryAdapterIsTheNamedPersistenceSeam() throws Exception {
        String adapter = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/users/internal/directory/LegacyUserDirectoryAdapter.java"));
        assertThat(adapter)
                .contains("UserRepository")
                .contains("model.users.User")
                .contains("@Deprecated");
    }
}
