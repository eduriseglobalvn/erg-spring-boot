package com.erg.ergspringboot.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.platform.concurrency.ErgReadExecutorProperties;
import com.erg.ergspringboot.platform.events.ErgModuleEventsProperties;
import com.erg.ergspringboot.platform.security.ErgAuthProperties;
import com.erg.ergspringboot.platform.web.ErgHttpProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** BE-045 contract tests for bounded, native-friendly configuration namespaces. */
class TypedConfigurationContractTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TypedConfiguration.class);

    @Test
    void bindsBackwardCompatibleDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            ErgReadExecutorProperties read = context.getBean(ErgReadExecutorProperties.class);
            assertThat(read.getCoreSize()).isEqualTo(8);
            assertThat(read.getMaxSize()).isEqualTo(16);
            assertThat(read.getQueueCapacity()).isEqualTo(64);
            assertThat(read.getShutdownTimeout()).isEqualTo(Duration.ofSeconds(5));

            ErgModuleEventsProperties events = context.getBean(ErgModuleEventsProperties.class);
            assertThat(events.getRecovery().getBatchSize()).isEqualTo(100);
            assertThat(events.getRecovery().getMaxInFlight()).isEqualTo(16);
            assertThat(events.getIo().getCoreSize()).isEqualTo(8);
            assertThat(events.getIo().getMaxSize()).isEqualTo(16);
            assertThat(events.getCpu().getParallelism()).isEqualTo(4);

            ErgHttpProperties http = context.getBean(ErgHttpProperties.class);
            assertThat(http.getCors().getAllowedMethods()).contains("GET", "OPTIONS");
            assertThat(http.getCors().getMaxAge()).isEqualTo(86400L);
            assertThat(http.getTrustedProxyCidrs()).isEmpty();

            ErgAuthProperties auth = context.getBean(ErgAuthProperties.class);
            assertThat(auth.isBffEnabled()).isFalse();
            assertThat(auth.getBffTenantId()).isEqualTo("erg");
            assertThat(auth.getRapidSessionReplacement().getThreshold()).isEqualTo(5);
            assertThat(auth.getRapidSessionReplacement().getWindowMinutes()).isEqualTo(10);
            assertThat(auth.getLoginRateLimit().getSourcePerMinute()).isEqualTo(30);
            assertThat(auth.getLoginRateLimit().getAccountPerMinute()).isEqualTo(10);
            assertThat(auth.getLoginRateLimit().getDevicePerMinute()).isEqualTo(20);
        });
    }

    @Test
    void rejectsInvalidReadConcurrency() {
        contextRunner.withPropertyValues(
                        "erg.performance.read-executor.core-size=8",
                        "erg.performance.read-executor.max-size=4")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsInvalidEventBounds() {
        contextRunner.withPropertyValues(
                        "erg.module-events.io.queue-capacity=129",
                        "erg.module-events.recovery.fixed-delay=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsInvalidCorsAndBffTenantSettings() {
        contextRunner.withPropertyValues(
                        "erg.http.cors.max-age=-1",
                        "erg.auth.bff-tenant-id= ",
                        "erg.auth.login-rate-limit.source-per-minute=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void migratedNamespacesHaveNoRawValueInjectionAndDeadFlagsAreGone() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/concurrency/ErgReadTaskExecutor.java"))
                + Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/concurrency/ModuleEventTaskExecutorConfig.java"))
                + Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/events/ModuleEventRecoveryJob.java"))
                + Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/web/CorsConfig.java"))
                + Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/TrustedClientIpResolver.java"))
                + Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/ErgAuthenticationFilter.java"))
                + Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java"))
                + Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/OidcPrincipalBridgeFilter.java"))
                + Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/auth/internal/login/AuthLoginUseCase.java"))
                + Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/auth/AuthLoginRateLimiter.java"));

        assertThat(source).doesNotContain("@Value(\"${erg.module-events")
                .doesNotContain("@Value(\"${erg.performance.read-executor")
                .doesNotContain("@Value(\"${erg.http.cors")
                .doesNotContain("@Value(\"${erg.http.trusted-proxy-cidrs")
                .doesNotContain("@Value(\"${erg.auth.");

        assertThat(Files.readString(Path.of("src/main/resources/application.yml")))
                .doesNotContain("new-handler-enabled")
                .doesNotContain("MODULES__ENABLED");
        assertThat(Files.readString(Path.of(".env.example")))
                .doesNotContain("MODULES__ENABLED");
    }

    @Test
    void configurationPropertiesAreNativeDiscoverableTypes() {
        assertThat(ErgReadExecutorProperties.class.getAnnotation(
                org.springframework.boot.context.properties.ConfigurationProperties.class)).isNotNull();
        assertThat(ErgModuleEventsProperties.class.getAnnotation(
                org.springframework.boot.context.properties.ConfigurationProperties.class)).isNotNull();
        assertThat(ErgHttpProperties.class.getAnnotation(
                org.springframework.boot.context.properties.ConfigurationProperties.class)).isNotNull();
        assertThat(ErgAuthProperties.class.getAnnotation(
                org.springframework.boot.context.properties.ConfigurationProperties.class)).isNotNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            ErgReadExecutorProperties.class,
            ErgModuleEventsProperties.class,
            ErgHttpProperties.class,
            ErgAuthProperties.class
    })
    static class TypedConfiguration {
    }
}
