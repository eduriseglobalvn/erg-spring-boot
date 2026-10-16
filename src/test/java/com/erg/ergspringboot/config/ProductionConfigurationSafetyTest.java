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
        // BE-014 findings #4/#6: auth cookies must default to Secure so sessions never travel over
        // plain HTTP unless an operator explicitly opts out.
        String common = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(common).contains("secure: ${AUTH__COOKIE__SECURE:true}");
    }

    @Test
    void productionProfilePinsSecurityHardening() throws Exception {
        // BE-014 findings #1/#4/#6: the prod profile hardcodes introspection off, Secure/strict
        // cookies and no-override error masking, so no env var can widen them in production.
        String common = Files.readString(Path.of("src/main/resources/application.yml"));
        int prodProfile = common.indexOf("on-profile: prod");
        assertThat(prodProfile).isPositive();
        String prodSection = common.substring(prodProfile);

        assertThat(prodSection)
                .contains("introspection:")
                .contains("enabled: false")
                .contains("secure: true")
                .contains("same-site: strict");
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
