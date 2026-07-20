package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WebhookSecurityBoundaryTest {

    @Test
    void securityConfigDoesNotPermitAllWebhookPaths() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java"));

        assertThat(source).doesNotContain("\"/webhooks/**\"");
    }
}
