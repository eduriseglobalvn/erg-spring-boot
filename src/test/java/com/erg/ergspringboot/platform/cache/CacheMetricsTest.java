package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class CacheMetricsTest {

    @Test
    void repeatedOperationsIncrementOneCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CacheMetrics metrics = new CacheMetrics(registry);

        metrics.record("c", "l1", "get", "hit");
        metrics.record("c", "l1", "get", "hit");
        metrics.record("c", "l1", "get", "miss");

        assertThat(registry.get("erg.cache.operations").tag("outcome", "hit").counter().count()).isEqualTo(2.0);
        assertThat(registry.find("erg.cache.operations").counters()).hasSize(2);
    }
}
