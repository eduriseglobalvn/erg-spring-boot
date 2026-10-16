package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthSecurityQueryArchitectureTest {

    @Test
    void loginAttemptAndIpSecurityQueriesHaveAnInternalOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.auth.internal.security.AuthSecurityQueryUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/auth/AuthOperations.java"));
        assertThat(facade)
                .contains("securityQueries.loginAttempts(")
                .contains("securityQueries.ipStatus(");
    }
}
