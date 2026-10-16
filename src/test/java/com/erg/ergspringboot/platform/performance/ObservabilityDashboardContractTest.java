package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObservabilityDashboardContractTest {

    @Test
    void dashboardsCoverRestGraphqlDatabaseCacheAndEventSloSignals() throws Exception {
        String dashboard = Files.readString(Path.of("ops/observability/grafana/erg-backend-slo.json"));
        String application = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(dashboard)
                .contains("http_server_requests_seconds")
                .contains("graphql_operation_duration_seconds")
                .contains("hikaricp_connections_pending")
                // The panel must query the metric the app actually emits (erg.cache.operations ->
                // erg_cache_operations_total), not a name that would silently render an empty panel.
                .contains("erg_cache_operations_total")
                .contains("spring_modulith_events")
                .contains("requestId")
                .contains("traceId");
        assertThat(application)
                .contains("tracing:")
                .contains("sampling:")
                .contains("probability: ${MANAGEMENT_TRACING_SAMPLING_PROBABILITY:0.1}")
                .contains("zipkin:");
    }
}
