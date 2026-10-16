package com.erg.ergspringboot.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Fail-closed CORS contract (BE-014 finding #3). A wildcard origin combined with
 * allow-credentials=true is a spec violation that lets any site read authenticated responses;
 * the config must refuse to start rather than silently serve it.
 */
class CorsConfigSecurityTest {

    private static final String METHODS = "GET,POST,PUT,DELETE,PATCH,OPTIONS";
    private static final String HEADERS = "Accept,Authorization,Content-Type";
    private static final String EXPOSED = "X-Request-ID";

    @Test
    void rejectsWildcardOriginWhenCredentialsAllowed() {
        assertThatThrownBy(() -> new CorsConfig(
                prodEnvironment(), "*", METHODS, HEADERS, EXPOSED, true, 86400))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard origin")
                .hasMessageContaining("allow-credentials");
    }

    @Test
    void rejectsPartialWildcardOriginWhenCredentialsAllowed() {
        assertThatThrownBy(() -> new CorsConfig(
                prodEnvironment(), "https://*.erg.edu.vn", METHODS, HEADERS, EXPOSED, true, 86400))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard origin");
    }

    @Test
    void allowsWildcardOnlyWhenCredentialsDisabled() {
        CorsConfig config = new CorsConfig(
                prodEnvironment(), "*", METHODS, HEADERS, EXPOSED, false, 86400);

        assertThat(config).isNotNull();
    }

    @Test
    void acceptsExplicitOriginsWithCredentials() {
        CorsConfig config = new CorsConfig(
                prodEnvironment(), "https://lms.erg.edu.vn", METHODS, HEADERS, EXPOSED, true, 86400);

        assertThat(config).isNotNull();
    }

    @Test
    void bffAllowlistRemovesClientAuthorizationAndTenantHeaders() {
        CorsConfig config = new CorsConfig(
                prodEnvironment(),
                "https://lms.erg.edu.vn",
                METHODS,
                "Accept,Authorization,Content-Type,X-Tenant-ID,X-Portal,X-Current-Class-ID",
                EXPOSED,
                true,
                86400,
                true);

        assertThat(config.allowedHeadersForTest())
                .contains("Accept", "Content-Type", "X-XSRF-TOKEN")
                .doesNotContain("Authorization", "X-Tenant-ID", "X-Portal", "X-Current-Class-ID");
    }

    private MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
