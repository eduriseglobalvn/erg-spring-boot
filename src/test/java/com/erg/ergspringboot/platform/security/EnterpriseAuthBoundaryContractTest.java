package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EnterpriseAuthBoundaryContractTest {
    @Test
    void enterpriseProfileDisablesLocalPasswordLogin() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application-enterprise-auth.yml"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/auth/AuthController.java"));
        assertThat(yaml).contains("local-login-enabled: false");
        assertThat(controller).contains("erg.auth.local-login-enabled");
    }
}
