package com.erg.ergspringboot.platform.cache;

import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;

public final class RedisTemplateCacheStore implements RedisCacheStore {

    private final RedisTemplate<String, byte[]> redis;

    public RedisTemplateCacheStore(RedisTemplate<String, byte[]> redis) {
        this.redis = redis;
    }

    @Override
    public byte[] get(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public void put(String key, byte[] value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public void evict(String key) {
        redis.delete(key);
    }
}

