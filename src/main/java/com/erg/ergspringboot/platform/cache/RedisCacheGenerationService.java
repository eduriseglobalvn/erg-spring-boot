package com.erg.ergspringboot.platform.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.ConcurrentMap;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisCacheGenerationService implements CacheGenerationService {

    private final StringRedisTemplate redis;
    // Bounded memo of Redis: an evicted entry is re-read from Redis, and scoped generations add one key per scope.
    private final ConcurrentMap<String, Long> local = Caffeine.newBuilder().maximumSize(100_000).<String, Long>build().asMap();

    public RedisCacheGenerationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long current(String tenantId, String cacheName) {
        String key = key(tenantId, cacheName);
        Long cached = local.get(key);
        if (cached != null) return cached;
        try {
            String value = redis.opsForValue().get(key);
            long generation = value == null ? 0 : Long.parseLong(value);
            local.put(key, generation);
            return generation;
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    @Override
    public long increment(String tenantId, String cacheName) {
        String key = key(tenantId, cacheName);
        try {
            Long generation = redis.opsForValue().increment(key);
            if (generation == null) return -1;
            local.put(key, generation);
            return generation;
        } catch (RuntimeException exception) {
            local.remove(key);
            return -1;
        }
    }

    public void acceptRemote(String tenantId, String cacheName, long generation) {
        local.merge(key(tenantId, cacheName), generation, Math::max);
    }

    private String key(String tenantId, String cacheName) {
        return "erg:state:v1:cache-generation:" + hash(tenantId) + ':' + cacheName;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}

