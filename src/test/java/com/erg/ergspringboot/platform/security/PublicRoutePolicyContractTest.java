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
    private static final Path PAGE_CONTROLLER = Path.of(
            "src/main/java/com/erg/ergspringboot/controller/cms/PageMenuController.java");
    private static final Path COURSE_CONTROLLER = Path.of(
            "src/main/java/com/erg/ergspringboot/controller/catalog/CourseController.java");
    private static final Path TRENDING_CONTROLLER = Path.of(
            "src/main/java/com/erg/ergspringboot/controller/trending/TrendingBotController.java");

    @Test
    void cmsProtectedVariantsPrecedeBroadPublicPostsRead() throws Exception {
        String source = Files.readString(SECURITY_CONFIG);
        int protectedIndex = source.indexOf("/api/v1/posts/hidden");
        int publicIndex = source.indexOf("/api/v1/posts/**");

        assertThat(protectedIndex)
                .as("editorial CMS variants must have an explicit protected matcher")
                .isGreaterThanOrEqualTo(0);
        assertThat(publicIndex)
                .as("public CMS posts matcher must remain explicit")
                .isGreaterThan(protectedIndex);
        assertThat(source.substring(protectedIndex, publicIndex))
                .contains("permissions.requireAny");
        int reviewProtectedIndex = source.indexOf("/api/v1/reviews/admin");
        assertThat(reviewProtectedIndex)
                .as("admin review listings must be protected before public review reads")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(publicIndex);
        assertThat(source.substring(reviewProtectedIndex, publicIndex)).contains(".authenticated()");
    }

    @Test
    void signedUploadIsTheOnlyReviewedPublicBusinessEndpoint() throws Exception {
        String mediaSource = Files.readString(MEDIA_CONTROLLER);
        assertThat(mediaSource).contains("@ReviewedPublicEndpoint");

        String securitySource = Files.readString(SECURITY_CONFIG);
        assertThat(securitySource).contains("/api/v1/lcms/media/upload/quiz-media/**");
        assertThat(securitySource).doesNotContain("/api/v1/lcms/**").doesNotContain("/api/v1/lms/**");
    }

    @Test
    void publicReadControllersDeclareReviewedEndpoints() throws Exception {
        assertThat(Files.readString(PAGE_CONTROLLER)).contains("@ReviewedPublicEndpoint");
        assertThat(Files.readString(COURSE_CONTROLLER)).contains("@ReviewedPublicEndpoint");
        assertThat(Files.readString(TRENDING_CONTROLLER)).contains("@ReviewedPublicEndpoint");
    }
}
