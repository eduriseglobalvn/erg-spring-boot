package com.erg.ergspringboot.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Loads the real application.yml the way Spring Boot does for a given profile and proves the
 * security-relevant {@code erg.*} bindings are not trapped inside a single profile document.
 */
class ApplicationYamlProfileBindingTest {

    @Test
    void prodProfileBindsCorsAndTrustedProxiesFromDeploymentEnvironment() {
        Binder binder = binder(Map.of(
                "HTTP__CORS__ALLOWED_ORIGINS", "https://lms.erg.edu.vn",
                "HTTP__TRUSTED_PROXY_CIDRS", "10.0.0.0/8"), "prod");

        ErgHttpProperties http = binder.bind("erg.http", ErgHttpProperties.class).get();

        assertThat(http.getCors().getAllowedOrigins()).isEqualTo("https://lms.erg.edu.vn");
        assertThat(http.getTrustedProxyCidrs()).isEqualTo("10.0.0.0/8");
    }

    @Test
    void prodProfileRequiresPushTokenEncryptionAndBindsTheKey() {
        Binder binder = binder(Map.of(
                "NOTIFICATIONS__PUSH__REGISTRATION_TOKEN_ENCRYPTION_KEY", "env-provided-key"), "prod");

        NotificationPushProperties push = binder.bind("erg.notifications.push", NotificationPushProperties.class).get();

        assertThat(push.getCredentials().getRegistrationTokenEncryptionKey()).isEqualTo("env-provided-key");
        assumeNoLocalDotEnv();
        assertThat(push.getCredentials().isRegistrationTokenEncryptionRequired()).isTrue();
    }

    @Test
    void sharedDocumentAppliesToEveryProfile() {
        for (String profile : new String[] {"dev", "staging", "prod"}) {
            Binder binder = binder(Map.of("HTTP__TRUSTED_PROXY_CIDRS", "172.16.0.0/12"), profile);

            assertThat(binder.bind("erg.http.trusted-proxy-cidrs", String.class).get())
                    .as(profile).isEqualTo("172.16.0.0/12");
            assertThat(binder.bind("server.forward-headers-strategy", String.class).get())
                    .as(profile).isEqualTo("native");
            assertThat(binder.bind("spring.mail.properties.mail.smtp.timeout", String.class).get())
                    .as(profile).isEqualTo("5000");
            assertThat(binder.bind("spring.task.scheduling.pool.size", Integer.class).get())
                    .as(profile).isEqualTo(4);
            assertThat(binder.bind("management.endpoint.health.show-details", String.class).get())
                    .as(profile).isEqualTo("never");
        }
    }

    @Test
    void mailDefaultsUseStartTlsRatherThanImplicitTls() {
        assumeNoLocalDotEnv();
        Binder binder = binder(Map.of(), "prod");

        assertThat(binder.bind("spring.mail.properties.mail.smtp.ssl.enable", String.class).get()).isEqualTo("false");
        assertThat(binder.bind("spring.mail.properties.mail.smtp.starttls.enable", String.class).get()).isEqualTo("true");
    }

    /** A developer .env is imported by application.yml and may legitimately override defaults. */
    private void assumeNoLocalDotEnv() {
        org.junit.jupiter.api.Assumptions.assumeFalse(new java.io.File(".env").exists(), "local .env overrides defaults");
    }

    private Binder binder(Map<String, Object> env, String profile) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env));
        environment.setActiveProfiles(profile);
        ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(),
                new DefaultBootstrapContext());
        return Binder.get(environment);
    }
}
