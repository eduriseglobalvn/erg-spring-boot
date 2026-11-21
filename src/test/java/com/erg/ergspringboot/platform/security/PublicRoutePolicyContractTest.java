package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Prevents broad public matchers from silently exposing editorial/admin variants. */
class PublicRoutePolicyContractTest {

    private static final Path SECURITY_CONFIG = Path.of(
            "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java");
    private static final Path MEDIA_CONTROLLER = Path.of(
            "src/main/java/com/erg/ergspringboot/controller/lcms/LcmsMediaController.java");

    @Test
    void signedUploadIsTheOnlyReviewedPublicBusinessEndpoint() throws Exception {
        String mediaSource = Files.readString(MEDIA_CONTROLLER);
        assertThat(mediaSource).contains("@ReviewedPublicEndpoint");

        String securitySource = Files.readString(SECURITY_CONFIG);
        assertThat(securitySource).contains("/api/v1/lcms/media/upload/quiz-media/**");
        assertThat(securitySource).doesNotContain("/api/v1/lcms/**").doesNotContain("/api/v1/lms/**");
    }
}
