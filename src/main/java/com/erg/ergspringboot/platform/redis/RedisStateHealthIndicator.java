package com.erg.ergspringboot.platform.redis;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

public final class RedisStateHealthIndicator implements HealthIndicator {
    private final RedisConnectionFactory connectionFactory;

    public RedisStateHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            return "PONG".equalsIgnoreCase(pong)
                    ? Health.up().withDetail("role", "state").build()
                    : Health.down().withDetail("role", "state").build();
        } catch (RuntimeException failure) {
            return Health.down().withDetail("role", "state")
                    .withDetail("reason", failure.getClass().getSimpleName()).build();
        }
    }
}
