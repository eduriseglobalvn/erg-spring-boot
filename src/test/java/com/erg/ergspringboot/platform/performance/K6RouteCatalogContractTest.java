package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class K6RouteCatalogContractTest {

    @Test
    void fiveThousandUserScriptsUseCurrentVersionedApiRoutes() throws Exception {
        String steady = Files.readString(Path.of("load/k6/erg-5k-steady.js"));
        String burst = Files.readString(Path.of("load/k6/erg-5k-submission-burst.js"));
        String redis = Files.readString(Path.of("load/k6/redis-8-8-cache.js"));
        String webhook = Files.readString(Path.of("load/k6/webhook-burst.js"));

        assertThat(steady).contains("/api/v1/lms/students/me/assignments").doesNotContain("/api/lms/assignments");
        assertThat(burst).contains("/api/v1/lms/attempts/${attemptId}/submit");
        assertThat(redis).contains(
                "/api/v1/lms/students/me/assignments",
                "/api/v1/lms/attempts/${attemptId}/submit")
                .doesNotContain("/api/v1/elearning/dashboard", "/api/lms/assignments", "load-attempt/submit");
        assertThat(webhook)
                .contains("/webhooks/${data.provider}", "update_id: (__VU * 1000000) + __ITER")
                .doesNotContain("/api/v1/webhooks/");
    }
}
