package com.erg.ergspringboot.platform.graphql.ratelimit;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisCapabilityDetector {

    private final StringRedisTemplate redis;
    private volatile Boolean increxSupported;

    public RedisCapabilityDetector(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean supportsIncrex() {
        Boolean cached = increxSupported;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (increxSupported == null) {
                Boolean supported = redis.execute((RedisCallback<Boolean>) connection -> {
                    java.util.List<Object> reply = LettuceNativeAccess.commands(connection).commandInfo("INCREX");
                    return reply != null && !reply.isEmpty() && reply.get(0) != null;
                });
                increxSupported = Boolean.TRUE.equals(supported);
            }
            return increxSupported;
        }
    }

    public void reset() {
        increxSupported = null;
    }
}
