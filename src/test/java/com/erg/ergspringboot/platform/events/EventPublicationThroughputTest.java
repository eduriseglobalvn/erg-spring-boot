package com.erg.ergspringboot.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Preflight contract for the external k6 throughput gate; numeric results remain release evidence. */
class EventPublicationThroughputTest {
    @Test
    void benchmarkIsBoundedAndHasExplicitLatencyAndErrorGates() throws Exception {
        String script = Files.readString(Path.of("load/k6/modulith-events.js"));
        assertThat(script)
                .contains("constant-arrival-rate")
                .contains("maxVUs")
                .contains("http_req_failed: ['rate<0.01']")
                .contains("http_req_duration: ['p(95)<800']")
                .doesNotContain("while (true)");
    }

    @Test
    void releaseRecordRequiresRegistryOverheadBelowTenPercent() throws Exception {
        String record = Files.readString(Path.of("docs/performance/modulith-event-overhead.md"));
        assertThat(record)
                .contains("p95 by more than 10%")
                .contains("publication lag")
                .contains("PostgreSQL WAL/IO")
                .contains("connection-pool wait")
                .contains("20,000 independent DRAFT batches")
                .contains("remains explicitly open");
    }
}
