package com.erg.ergspringboot.platform.redis;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

public final class RedisCacheHealthIndicator implements HealthIndicator {
    private final RedisConnectionFactory connectionFactory;

    public RedisCacheHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            return "PONG".equalsIgnoreCase(pong)
                    ? Health.up().withDetail("role", "cache").build()
                    : degraded("unexpected_ping");
        } catch (RuntimeException failure) {
            return degraded(failure.getClass().getSimpleName());
        }
    }

    private Health degraded(String reason) {
        return Health.status("DEGRADED").withDetail("role", "cache")
                .withDetail("fallback", "postgresql").withDetail("reason", reason).build();
    }
}
