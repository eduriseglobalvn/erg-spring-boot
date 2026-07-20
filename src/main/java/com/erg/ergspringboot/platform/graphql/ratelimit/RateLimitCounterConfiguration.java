package com.erg.ergspringboot.platform.graphql.ratelimit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "erg.redis.state", name = "enabled", havingValue = "true")
public class RateLimitCounterConfiguration {

    @Bean
    RedisCapabilityDetector redisCapabilityDetector(
            @Qualifier("stateStringRedisTemplate") StringRedisTemplate redis
    ) {
        return new RedisCapabilityDetector(redis);
    }

    @Bean
    Redis88IncrexRateLimitCounter redis88IncrexRateLimitCounter(
            @Qualifier("stateStringRedisTemplate") StringRedisTemplate redis
    ) {
        return new Redis88IncrexRateLimitCounter(redis);
    }

    @Bean
    LuaFixedWindowRateLimitCounter luaFixedWindowRateLimitCounter(
            @Qualifier("stateStringRedisTemplate") StringRedisTemplate redis
    ) {
        return new LuaFixedWindowRateLimitCounter(redis);
    }

    @Bean
    GraphQlRateLimitCounterRouter graphQlRateLimitCounterRouter(
            RedisCapabilityDetector capabilities,
            Redis88IncrexRateLimitCounter increx,
            LuaFixedWindowRateLimitCounter lua
    ) {
        return new GraphQlRateLimitCounterRouter(capabilities, increx, lua);
    }
}

