package com.erg.ergspringboot.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphQlOperationMetricTagTest {

    @Test
    void allowlistReportsOnlyKnownOperations() {
        GraphQlOperationMetricTag tag = new GraphQlOperationMetricTag(Set.of("StudentDashboard"), 100);

        assertThat(tag.tagFor("StudentDashboard")).isEqualTo("StudentDashboard");
        assertThat(tag.tagFor("q1")).isEqualTo("other");
    }

    @Test
    void withoutAllowlistDistinctNamesAreCappedAndMalformedNamesCollapse() {
        GraphQlOperationMetricTag tag = new GraphQlOperationMetricTag(Set.of(), 2);

        assertThat(tag.tagFor("A")).isEqualTo("A");
        assertThat(tag.tagFor("B")).isEqualTo("B");
        assertThat(tag.tagFor("C")).isEqualTo("other");
        assertThat(tag.tagFor("A")).isEqualTo("A");
        assertThat(tag.tagFor("x".repeat(200))).isEqualTo("other");
        assertThat(tag.tagFor("bad name;")).isEqualTo("other");
        assertThat(tag.tagFor(null)).isEqualTo("other");
    }

    @Test
    void meterFilterCapsOperationTagValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new GraphQlMetricsConfiguration().graphQlOperationTagCardinalityLimit());

        for (int index = 0; index < GraphQlMetricsConfiguration.MAX_OPERATION_TAG_VALUES + 50; index++) {
            Timer.builder(GraphQlMetricsConfiguration.OPERATION_DURATION_METER)
                    .tag("operation", "op" + index).tag("result", "success").register(registry);
        }

        assertThat(registry.find(GraphQlMetricsConfiguration.OPERATION_DURATION_METER).timers())
                .hasSize(GraphQlMetricsConfiguration.MAX_OPERATION_TAG_VALUES);
    }
}
