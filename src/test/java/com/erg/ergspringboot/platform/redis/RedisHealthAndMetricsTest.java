package com.erg.ergspringboot.platform.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.platform.cache.CacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class RedisHealthAndMetricsTest {

    @Test
    void stateFailureIsDownButCacheFailureIsOnlyDegraded() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenThrow(new IllegalStateException("offline"));

        assertThat(new RedisStateHealthIndicator(factory).health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(new RedisCacheHealthIndicator(factory).health().getStatus().getCode()).isEqualTo("DEGRADED");
    }

    @Test
    void cacheMetricsUseOnlyBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CacheMetrics metrics = new CacheMetrics(registry);
        metrics.record("studentDashboard", "l2", "get", "hit");
        metrics.recordSerialization("studentDashboard", 123, 1_000_000);

        Set<String> tagKeys = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey())
                .collect(Collectors.toSet());
        assertThat(tagKeys).isSubsetOf("cache", "tier", "operation", "outcome");
        assertThat(tagKeys).doesNotContain("tenant", "user", "key", "exception", "message");
    }
}
