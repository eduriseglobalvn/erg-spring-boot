package com.erg.ergspringboot.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GraphQlRateLimiterTest {

    @Test
    void localFixedWindowAllowsConfiguredLimitThenRejects() {
        ErgGraphQlProperties properties = new ErgGraphQlProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setRedisEnabled(false);
        properties.getRateLimit().setRequestsPerMinute(2);
        properties.getRateLimit().setBurst(1);
        GraphQlRateLimiter limiter = new GraphQlRateLimiter(properties);
        GraphQlRateLimitKey key = new GraphQlRateLimitKey("tenant-a", "user-a", "StudentDashboard");

        assertThat(limiter.allow(key).allowed()).isTrue();
        assertThat(limiter.allow(key).allowed()).isTrue();
        assertThat(limiter.allow(key).allowed()).isTrue();
        GraphQlRateLimiter.Decision rejected = limiter.allow(key);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
    }
}
