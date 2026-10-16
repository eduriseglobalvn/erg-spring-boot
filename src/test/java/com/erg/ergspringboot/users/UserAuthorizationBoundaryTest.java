package com.erg.ergspringboot.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserAuthorizationBoundaryTest {

    @Test
    void publicAuthorizationPortDoesNotExposeJPATypes() throws Exception {
        String port = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/users/UserAuthorizationPort.java"));
        String projection = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/users/UserAuthorizationProjection.java"));
        assertThat(port).doesNotContain("UserRepository").doesNotContain("model.authorization");
        assertThat(projection).doesNotContain("UserRepository").doesNotContain("@Entity");
    }

    @Test
    void authorizationSnapshotIsImmutableAndFailClosed() {
        UserAuthorizationProjection snapshot = new UserAuthorizationProjection(
                "u-1", "tenant-1", List.of("teacher"),
                List.of("calendar.read", "calendar.write"),
                List.of("reports.read"), List.of("calendar.write"));

        assertThat(snapshot.effectivePermissions())
                .containsExactly("calendar.read", "reports.read");
        assertThatThrownBy(() -> snapshot.rolePermissions().add("admin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void authorizationReadUseCaseUsesTheTypedPort() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/authorization/internal/access/AuthorizationAccessQueryUseCase.java"));
        assertThat(source)
                .contains("UserAuthorizationPort")
                .doesNotContain("UserRepository")
                .doesNotContain("model.users.User");
    }
}
