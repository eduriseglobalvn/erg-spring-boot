package com.erg.ergspringboot.platform.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

public final class CacheMetrics {
    private final MeterRegistry registry;

    public CacheMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String cache, String tier, String operation, String outcome) {
        Counter.builder("erg.cache.operations")
                .description("Tiered cache operations")
                .tags("cache", bounded(cache), "tier", bounded(tier),
                        "operation", bounded(operation), "outcome", bounded(outcome))
                .register(registry).increment();
    }

    public void recordSerialization(String cache, int bytes, long nanos) {
        DistributionSummary.builder("erg.cache.serialization.bytes")
                .baseUnit("bytes").tag("cache", bounded(cache)).tag("outcome", "encoded")
                .register(registry).record(Math.max(0, bytes));
        Timer.builder("erg.cache.serialization.time")
                .tag("cache", bounded(cache)).tag("operation", "encode")
                .register(registry).record(Duration.ofNanos(Math.max(0, nanos)));
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.length() <= 96 ? value : value.substring(0, 96);
    }
}
