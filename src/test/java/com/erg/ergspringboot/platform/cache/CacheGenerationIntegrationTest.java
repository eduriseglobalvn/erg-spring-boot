package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

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
class CacheGenerationIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.8.0-alpine3.23")).withExposedPorts(6379);

    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (factory != null) factory.destroy();
    }

    @Test
    void replicasConvergeWhenSubscriberAcceptsPublishedGeneration() {
        RedisCacheGenerationService first = new RedisCacheGenerationService(redis);
        RedisCacheGenerationService second = new RedisCacheGenerationService(redis);

        assertThat(first.current("tenant-a", "studentDashboard")).isZero();
        assertThat(second.current("tenant-a", "studentDashboard")).isZero();
        long generation = first.increment("tenant-a", "studentDashboard");
        second.acceptRemote("tenant-a", "studentDashboard", generation);

        assertThat(generation).isEqualTo(1);
        assertThat(second.current("tenant-a", "studentDashboard")).isEqualTo(1);
        assertThat(second.current("tenant-b", "studentDashboard")).isZero();
    }
}

