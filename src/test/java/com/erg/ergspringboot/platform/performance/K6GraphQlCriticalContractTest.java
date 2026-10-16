package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class K6GraphQlCriticalContractTest {

    @Test
    void graphqlScenarioPinsEndpointAuthOperationAndSlo() throws Exception {
        String script = Files.readString(Path.of("load/k6/erg-graphql-critical.js"));

        assertThat(script)
                .contains("/api/v1/graphql")
                .contains("query LmsNamespace")
                .contains("Authorization")
                .contains("X-Tenant-ID")
                .contains("target: 100")
                .contains("target: 500")
                .contains("target: 1000")
                .contains("target: 5000")
                .contains("p(50)<150")
                .contains("p(95)<300")
                .contains("p(99)<800")
                .contains("rate<0.001");
    }

    @Test
    void graphqlCorrectnessSmokeKeepsLatencySloForMeasuredRunsOnly() throws Exception {
        String script = Files.readString(Path.of("load/k6/erg-graphql-critical.js"));

        assertThat(script)
                .contains("const isSmoke = __ENV.SMOKE === 'true'")
                .contains("const targetVus = Number(__ENV.TARGET_VUS || 0)")
                .contains("__ENV.STEP_DURATION")
                .contains("thresholds: isSmoke || isWarmup ?")
                .contains("http_req_failed: ['rate<0.001']");
    }

    @Test
    void graphqlLoadDistributesRequestsAcrossDeterministicStudentSessions() throws Exception {
        String script = Files.readString(Path.of("load/k6/erg-graphql-critical.js"));

        assertThat(script)
                .contains("const studentNumber = ((__VU - 1) % 4500) + 1")
                .contains("erg-load-seed-student-${pad(studentNumber, 5)}")
                .contains("seed-tenant-${pad(tenantNumber, 2)}");
    }
}
