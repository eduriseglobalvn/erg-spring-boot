package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * BE-015 versioned load baseline. The steady-read critical-flow scenario must climb the agreed
 * representative ladder (100 -> 500 -> 1,000 -> 5,000 VUs) and enforce the p50/p95/p99 + error SLO
 * from docs/performance/2026-07-workload-model.md. This locks the baseline in source so a scenario
 * edit that drops a step or loosens an SLO fails CI rather than silently weakening the release gate.
 *
 * <p>This is the in-repo contract only. Measured p50/p95/p99 evidence under real 5,000-VU load
 * requires the seeded benchmark environment (see docs/performance/2026-07-workload-model.md and the
 * {@code release-load-gates} CI job); a unit test does not manufacture that evidence.
 */
class K6SteadyLoadBaselineContractTest {

    @Test
    void steadyScenarioClimbsRepresentativeLadderAndPinsSlo() throws Exception {
        String steady = Files.readString(Path.of("load/k6/erg-5k-steady.js"));

        // Representative step ladder: each named VU step must be present.
        assertThat(steady)
                .contains("target: 100")
                .contains("target: 500")
                .contains("target: 1000")
                .contains("target: 5000");

        // Versioned SLO thresholds for the cacheable-read critical flow.
        assertThat(steady)
                .contains("p(50)<150")
                .contains("p(95)<300")
                .contains("p(99)<800")
                .contains("rate<0.001");

        // Hits the current versioned critical-read route, not a legacy path.
        assertThat(steady)
                .contains("/api/v1/lms/students/me/assignments")
                .doesNotContain("/api/lms/assignments");
    }

    @Test
    void correctnessSmokeDoesNotFailOnColdStartLatencyBeforeWarmup() throws Exception {
        String steady = Files.readString(Path.of("load/k6/erg-5k-steady.js"));

        assertThat(steady)
                .contains("const isSmoke = __ENV.SMOKE === 'true'")
                .contains("const targetVus = Number(__ENV.TARGET_VUS || 0)")
                .contains("__ENV.STEP_DURATION")
                .contains("thresholds: isSmoke || isWarmup ?")
                .contains("http_req_failed: ['rate<0.001']");
    }

    @Test
    void submissionBurstPinsWriteSlo() throws Exception {
        String burst = Files.readString(Path.of("load/k6/erg-5k-submission-burst.js"));

        assertThat(burst)
                .contains("vus: 5000")
                .contains("p(95)<800")
                .contains("rate<0.001")
                .contains("/api/v1/lms/attempts/${attemptId}/submit");
    }
}
