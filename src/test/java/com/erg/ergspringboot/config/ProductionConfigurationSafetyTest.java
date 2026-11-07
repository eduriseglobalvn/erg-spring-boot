package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionConfigurationSafetyTest {

    @Test
    void commonConfigurationFailsSafeForProduction() throws Exception {
        String common = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(common)
                .doesNotContain("active: ${SPRING_PROFILES_ACTIVE:dev}")
                .contains("enabled: ${FLYWAY_ENABLED:true}")
                .contains("include-message: never")
                .contains("include-stacktrace: never")
                .contains("include-binding-errors: never")
                .contains("enabled: ${SPRINGDOC_API_DOCS_ENABLED:false}")
                .contains("enabled: ${SCALAR_ENABLED:false}")
                .contains("enabled: ${ERG_PROCESS_LOCAL_ENABLED:false}");
    }

    @Test
    void authCookieDefaultsToSecure() throws Exception {
        // BE-014 findings #4/#6: the only session cookie is the BFF servlet session cookie and it is
        // always Secure. The former erg.auth.cookie.* keys were never read (review N5) and are removed
        // so they cannot suggest a protection that does not exist.
        String common = Files.readString(Path.of("src/main/resources/application.yml"));
        String enterprise = Files.readString(Path.of("src/main/resources/application-enterprise-auth.yml"));

        assertThat(common).doesNotContain("AUTH__COOKIE__");
        assertThat(enterprise).contains("secure: true").contains("http-only: true");
    }

    @Test
    void productionProfilePinsSecurityHardening() throws Exception {
        // BE-014 findings #1/#6: the prod profile hardcodes introspection and API docs off and
        // requires push-token encryption, so no env var default can widen them in production.
        String common = Files.readString(Path.of("src/main/resources/application.yml"));
        int prodProfile = common.indexOf("on-profile: prod");
        assertThat(prodProfile).isPositive();
        String prodSection = common.substring(prodProfile);

        assertThat(prodSection)
                .contains("introspection:")
                .contains("enabled: false")
                .contains("registration-token-encryption-required: ${NOTIFICATIONS__PUSH__REGISTRATION_TOKEN_ENCRYPTION_REQUIRED:true}");
    }

    @Test
    void productionKeepsPunchSelfiesInThePrivateBucket() throws Exception {
        // Timekeeping T3: prod defaults the private R2 photo bucket on, so missing TK_PHOTOS_R2__* fails startup
        // instead of silently writing selfies to local disk (the local bean also refuses the prod profile).
        String common = Files.readString(Path.of("src/main/resources/application.yml")).replace("\r\n", "\n");
        String prodSection = common.substring(common.indexOf("on-profile: prod"));
        prodSection = prodSection.substring(0, prodSection.indexOf("\n---"));

        assertThat(prodSection).contains("enabled: ${TK_PHOTOS_R2__ENABLED:true}");
        assertThat(common).contains("enabled: ${TK_PHOTOS_R2__ENABLED:false}");
    }

    @Test
    void localConvenienceIsExplicitlyOptIn() throws Exception {
        String local = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertThat(local)
                .contains("generate_statistics: true")
                .contains("include-message: always")
                .contains("api-docs:")
                .contains("process-local:");
    }

    @Test
    void enterpriseAuthProfileUsesBffSessionAndHostCookieWithoutLocalTokenLogin() throws Exception {
        String enterprise = Files.readString(Path.of("src/main/resources/application-enterprise-auth.yml"));

        assertThat(enterprise)
                .contains("bff-enabled: true")
                .contains("local-login-enabled: false")
                .contains("name: __Host-erg_session")
                .contains("http-only: true")
                .contains("secure: true")
                .contains("same-site: lax")
                .contains("allowed-headers:")
                .contains("X-XSRF-TOKEN")
                .doesNotContain("X-Tenant-ID,X-Portal");
    }
}
