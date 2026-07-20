package com.erg.ergspringboot.platform.graphql.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FixedWindowRateLimitCounterIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.8.0-alpine3.23"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void increxAppliesExactlyTheBoundAndExpiresAtWindowEnd() {
        Redis88IncrexRateLimitCounter counter = new Redis88IncrexRateLimitCounter(redis);
        String key = "erg:state:v1:test:increx:" + UUID.randomUUID();
        Instant windowEnd = Instant.now().plusSeconds(30);

        assertThat(counter.increment(key, 3, windowEnd).incrementApplied()).isTrue();
        assertThat(counter.increment(key, 3, windowEnd).incrementApplied()).isTrue();
        assertThat(counter.increment(key, 3, windowEnd).incrementApplied()).isTrue();
        RateLimitCounterResult rejected = counter.increment(key, 3, windowEnd);

        assertThat(rejected.incrementApplied()).isFalse();
        assertThat(rejected.value()).isEqualTo(3);
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1L, 30_000L);
    }

    @Test
    void luaFallbackMatchesRedis88ObservableResult() {
        GraphQlRateLimitCounter increx = new Redis88IncrexRateLimitCounter(redis);
        GraphQlRateLimitCounter lua = new LuaFixedWindowRateLimitCounter(redis);
        Instant windowEnd = Instant.now().plusSeconds(30);
        String suffix = UUID.randomUUID().toString();

        for (int request = 1; request <= 4; request++) {
            RateLimitCounterResult nativeResult = increx.increment("erg:state:v1:test:native:" + suffix, 3, windowEnd);
            RateLimitCounterResult luaResult = lua.increment("erg:state:v1:test:lua:" + suffix, 3, windowEnd);
            assertThat(luaResult.value()).isEqualTo(nativeResult.value());
            assertThat(luaResult.incrementApplied()).isEqualTo(nativeResult.incrementApplied());
        }
    }

    @Test
    void capabilityDetectorFindsIncrexOnRedis88() {
        assertThat(new RedisCapabilityDetector(redis).supportsIncrex()).isTrue();
    }
}
