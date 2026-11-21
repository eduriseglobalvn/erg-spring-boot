package com.erg.ergspringboot.platform.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisCacheGenerationService implements CacheGenerationService {

    private final StringRedisTemplate redis;
    // Bounded memo of Redis keyed by the cheap tenant:cache string: an evicted or expired entry is re-read from Redis, and
    // scoped generations add one key per scope. Remote bumps arrive via pub/sub (acceptRemote); the 30 s expiry only bounds a lost message.
    private final ConcurrentMap<String, Long> local = Caffeine.newBuilder()
            .maximumSize(100_000).expireAfterWrite(Duration.ofSeconds(30)).<String, Long>build().asMap();
    // Keys whose last bump failed: L2 entries written before it may still be live, so current() says "unknown" until a bump succeeds.
    // A dead Redis is remembered for 3 s so each request does not wait out the command timeout again.
    private final ConcurrentMap<String, Boolean> failedReads = Caffeine.newBuilder()
            .maximumSize(100_000).expireAfterWrite(Duration.ofSeconds(3)).<String, Boolean>build().asMap();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public RedisCacheGenerationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long current(String tenantId, String cacheName) {
        String memo = memoKey(tenantId, cacheName);
        if (pending.contains(memo)) return -1;
        Long cached = local.get(memo);
        if (cached != null) return cached;
        if (failedReads.containsKey(memo)) return -1;
        try {
            String value = redis.opsForValue().get(key(tenantId, cacheName));
            long generation = value == null ? 0 : Long.parseLong(value);
            local.put(memo, generation);
            return generation;
        } catch (RuntimeException exception) {
            failedReads.put(memo, Boolean.TRUE);
            return -1;
        }
    }

    @Override
    public long increment(String tenantId, String cacheName) {
        String memo = memoKey(tenantId, cacheName);
        try {
            Long generation = redis.opsForValue().increment(key(tenantId, cacheName));
            if (generation == null) return fail(memo);
            local.put(memo, generation);
            pending.remove(memo);
            return generation;
        } catch (RuntimeException exception) {
            return fail(memo);
        }
    }

    private long fail(String memo) {
        pending.add(memo);
        local.remove(memo);
        return -1;
    }

    public void acceptRemote(String tenantId, String cacheName, long generation) {
        String memo = memoKey(tenantId, cacheName);
        local.merge(memo, generation, Math::max);
        // Another instance bumped successfully, which already moves every key written before our failed bump.
        pending.remove(memo);
    }

    private static String memoKey(String tenantId, String cacheName) {
        return tenantId + ':' + cacheName;
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

