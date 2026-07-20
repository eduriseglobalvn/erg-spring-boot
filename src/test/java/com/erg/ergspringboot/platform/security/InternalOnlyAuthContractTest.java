package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InternalOnlyAuthContractTest {

    @Test
    void publicAuthSurfaceContainsOnlyInternalLoginAndSessionEndpoints() throws Exception {
        String controller = read("src/main/java/com/erg/ergspringboot/controller/auth/AuthController.java");
        String security = read("src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java");

        assertThat(controller)
                .doesNotContain("/register")
                .doesNotContain("/google/login")
                .doesNotContain("/oauth/apple")
                .doesNotContain("/oauth/{provider}")
                .doesNotContain("/forgot-password")
                .doesNotContain("/reset-password");
        assertThat(security)
                .doesNotContain("/api/v1/auth/register")
                .doesNotContain("/api/v1/auth/google/login")
                .doesNotContain("/api/v1/auth/oauth")
                .doesNotContain("/api/v1/auth/forgot-password")
                .doesNotContain("/api/v1/auth/reset-password");
    }

    @Test
    void authRequestsDoNotAcceptSocialIdentityClaimsOrSelfRegistration() throws Exception {
        String requests = read("src/main/java/com/erg/ergspringboot/common/request/auth/AuthRequests.java");

        assertThat(requests)
                .doesNotContain("RegisterRequest")
                .doesNotContain("GoogleLoginRequest")
                .doesNotContain("SocialLoginRequest")
                .doesNotContain("ForgotPasswordRequest")
                .doesNotContain("ResetPasswordRequest");
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
