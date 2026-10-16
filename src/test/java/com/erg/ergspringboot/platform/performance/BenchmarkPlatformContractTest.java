package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BenchmarkPlatformContractTest {

    private static final Path BENCHMARK_COMPOSE = Path.of("ops/docker/compose.benchmark.yml");
    private static final Path DOCKERFILE = Path.of("Dockerfile");
    private static final Path APPLICATION_CONFIG = Path.of("src/main/resources/application.yml");
    private static final Path SECURITY_CONFIG = Path.of(
            "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java");

    @Test
    void composeOwnsFullIsolatedObservabilityPlatform() throws Exception {
        String compose = Files.readString(BENCHMARK_COMPOSE);

        assertThat(compose)
                .contains("name: erg-benchmark")
                .contains("backend-jvm:")
                .contains("backend-native:")
                .contains("postgres:")
                .contains("redis-state:")
                .contains("redis-cache:")
                .contains("prometheus:")
                .contains("grafana:")
                .contains("cadvisor:")
                .contains("ghcr.io/google/cadvisor:v0.57.0")
                .contains("/dev/kmsg:/dev/kmsg")
                .contains("postgres-exporter:")
                .contains("redis-state-exporter:")
                .contains("redis-cache-exporter:")
                .contains("k6:");
    }

    @Test
    void imagesExposeComparableNonRootRuntimeTargets() throws Exception {
        String dockerfile = Files.readString(DOCKERFILE);

        assertThat(dockerfile)
                .contains("AS jvm-runtime")
                .contains("AS native-runtime")
                .contains("USER erg")
                .contains("HEALTHCHECK");
    }

    @Test
    void composeBoundsResourcesAndPersistsOnlyResults() throws Exception {
        String compose = Files.readString(BENCHMARK_COMPOSE);

        assertThat(compose)
                .contains("mem_limit:")
                .contains("cpus:")
                .contains("load/results")
                .contains("read_only: true")
                .contains("SECRET_MEDIA_URL_SIGNING_KEY:")
                .doesNotContain("container_name:");
    }

    @Test
    void k6RemoteWriteMatchesTheProvisionedGrafanaPercentileSeries() throws Exception {
        String compose = Files.readString(BENCHMARK_COMPOSE);

        assertThat(compose)
                .contains("K6_PROMETHEUS_RW_TREND_STATS: p(50),p(95),p(99),avg,min,max")
                .doesNotContain("K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM: \"true\"");
    }

    @Test
    void benchmarkPoolCapacityIsExplicitAndLeavesPostgresHeadroom() throws Exception {
        String compose = Files.readString(BENCHMARK_COMPOSE);
        String environment = Files.readString(Path.of("ops/docker/.env.benchmark.example"));

        assertThat(compose)
                .contains("DB__MAX_OPEN_CONNS: ${BACKEND_DB_MAX_OPEN_CONNS:-80}")
                .contains("DB__MAX_IDLE_CONNS: ${BACKEND_DB_MIN_IDLE_CONNS:-25}");
        assertThat(environment)
                .contains("BACKEND_DB_MAX_OPEN_CONNS=80")
                .contains("BACKEND_DB_MIN_IDLE_CONNS=25")
                .contains("POSTGRES_MAX_CONNECTIONS=160");
    }

    @Test
    void nativeRuntimeLoadsFlywayMigrationsFromTheCopiedFilesystem() throws Exception {
        String compose = Files.readString(BENCHMARK_COMPOSE);
        String dockerfile = Files.readString(DOCKERFILE);

        assertThat(compose)
                .contains("FLYWAY_LOCATIONS: filesystem:/app/db/migration/postgres");
        assertThat(dockerfile)
                .contains("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus")
                .contains("MANAGEMENT_ENDPOINT_PROMETHEUS_ACCESS=read-only")
                .contains("MANAGEMENT_PROMETHEUS_METRICS_EXPORT_ENABLED=true");
    }

    @Test
    void prometheusAccessIsSecureByDefaultAndEnabledOnlyForTheBenchmarkPlatform() throws Exception {
        String application = Files.readString(APPLICATION_CONFIG);
        String compose = Files.readString(BENCHMARK_COMPOSE);
        String security = Files.readString(SECURITY_CONFIG);

        assertThat(application)
                .contains("prometheus-public-enabled: ${ERG_OBSERVABILITY_PROMETHEUS_PUBLIC_ENABLED:false}");
        assertThat(compose)
                .contains("ERG_OBSERVABILITY_PROMETHEUS_PUBLIC_ENABLED: \"true\"");
        assertThat(security)
                .contains(".requestMatchers(prometheusAccessRequestMatcher).permitAll()")
                .contains(".anyRequest().denyAll()");
    }
}
