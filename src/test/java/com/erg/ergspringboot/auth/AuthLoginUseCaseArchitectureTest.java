package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthLoginUseCaseArchitectureTest {

    @Test
    void loginPolicyAndTokenIssuanceHaveAnInternalTransactionalOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.auth.internal.login.AuthLoginUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/auth/AuthService.java"));
        assertThat(facade)
                .contains("loginFlow.login(")
                .doesNotContain("issueTokens(")
                .doesNotContain("recordSecurityEvent(");
    }
}
