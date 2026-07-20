package com.erg.ergspringboot.platform.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

public final class RedisCacheInvalidationPublisher implements CacheInvalidationPublisher {

    public static final String CHANNEL = "erg:state:v1:cache-invalidation";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisCacheInvalidationPublisher(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public void publish(CacheInvalidationMessage message) {
        redis.convertAndSend(CHANNEL, mapper.writeValueAsString(message));
    }
}

