package com.erg.ergspringboot.common.response.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportJobResponseTest {

    @Test
    void theStatusViewDropsTheStoredResultAndKeepsTheRest() {
        ReportJobResponse job = new ReportJobResponse("j1", "assignment-progress", "a1", "completed", 100, null, null,
                Map.of("rows", 10_000), null, "csv", null, "/api/v1/reports/jobs/j1/download");

        ReportJobResponse status = job.withoutResult();

        assertThat(status.result()).isNull();
        assertThat(status).usingRecursiveComparison().ignoringFields("result").isEqualTo(job);
        assertThat(status.withoutResult()).isSameAs(status);
    }
}
