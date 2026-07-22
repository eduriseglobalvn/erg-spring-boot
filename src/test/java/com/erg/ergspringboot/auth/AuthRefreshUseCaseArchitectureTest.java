package com.erg.ergspringboot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthRefreshUseCaseArchitectureTest {

    @Test
    void refreshRotationAndReplayHandlingHaveAnInternalTransactionalOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.auth.internal.refresh.AuthRefreshUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/auth/AuthService.java"));
        assertThat(facade)
                .contains("refreshTokens.refresh(")
                .doesNotContain("handleRefreshTokenReplay(");
    }
}
