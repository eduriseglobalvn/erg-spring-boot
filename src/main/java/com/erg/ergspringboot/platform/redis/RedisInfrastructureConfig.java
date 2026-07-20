package com.erg.ergspringboot.platform.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.health.contributor.HealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ErgRedisProperties.class)
@ImportRuntimeHints(RedisNativeRuntimeHints.class)
@EnableScheduling
public class RedisInfrastructureConfig {

    @Bean(name = {"redisConnectionFactory", "stateRedisConnectionFactory"})
    @Primary
    @ConditionalOnProperty(prefix = "erg.redis.state", name = "enabled", havingValue = "true")
    LettuceConnectionFactory stateRedisConnectionFactory(ErgRedisProperties properties) {
        return RedisClientFactory.create(properties.getState());
    }

    @Bean(name = {"stringRedisTemplate", "stateStringRedisTemplate"})
    @Primary
    @ConditionalOnProperty(prefix = "erg.redis.state", name = "enabled", havingValue = "true")
    StringRedisTemplate stateStringRedisTemplate(
            @Qualifier("stateRedisConnectionFactory") RedisConnectionFactory connectionFactory
    ) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean(name = "cacheRedisConnectionFactory")
    @ConditionalOnProperty(prefix = "erg.redis.cache", name = "enabled", havingValue = "true")
    LettuceConnectionFactory cacheRedisConnectionFactory(ErgRedisProperties properties) {
        return RedisClientFactory.create(properties.getCache());
    }

    @Bean
    @ConditionalOnProperty(prefix = "erg.redis.cache", name = "enabled", havingValue = "true")
    StringRedisTemplate cacheStringRedisTemplate(
            @Qualifier("cacheRedisConnectionFactory") RedisConnectionFactory connectionFactory
    ) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean(name = "redisStateHealth")
    @ConditionalOnProperty(prefix = "erg.redis.state", name = "enabled", havingValue = "true")
    HealthIndicator redisStateHealth(
            @Qualifier("stateRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        return new RedisStateHealthIndicator(connectionFactory);
    }

    @Bean(name = "redisCacheHealth")
    @ConditionalOnProperty(prefix = "erg.redis.cache", name = "enabled", havingValue = "true")
    HealthIndicator redisCacheHealth(
            @Qualifier("cacheRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        return new RedisCacheHealthIndicator(connectionFactory);
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "erg.redis.state", name = "enabled", havingValue = "true")
    RedisMetricsBinder redisStateMetrics(
            @Qualifier("stateRedisConnectionFactory") RedisConnectionFactory connectionFactory,
            MeterRegistry registry) {
        RedisMetricsBinder binder = new RedisMetricsBinder("state", connectionFactory);
        binder.bindTo(registry);
        return binder;
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "erg.redis.cache", name = "enabled", havingValue = "true")
    RedisMetricsBinder redisCacheMetrics(
            @Qualifier("cacheRedisConnectionFactory") RedisConnectionFactory connectionFactory,
            MeterRegistry registry) {
        RedisMetricsBinder binder = new RedisMetricsBinder("cache", connectionFactory);
        binder.bindTo(registry);
        return binder;
    }
}
