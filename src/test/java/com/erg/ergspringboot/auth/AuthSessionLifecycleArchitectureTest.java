package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthSessionLifecycleArchitectureTest {

    @Test
    void logoutAndAccountRevocationHaveAnInternalTransactionalOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.auth.internal.session.AuthSessionLifecycleUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/auth/AuthOperations.java"));
        assertThat(facade)
                .contains("sessionLifecycle.logout(")
                .contains("sessionLifecycle.logoutAll(");
    }
}
