package com.erg.ergspringboot.platform.graphql.ratelimit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class LuaFixedWindowRateLimitCounter implements GraphQlRateLimitCounter {

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local upper = tonumber(ARGV[1])
            if current >= upper then
              return {current, 0}
            end
            local next = redis.call('INCR', KEYS[1])
            if redis.call('PTTL', KEYS[1]) < 0 then
              redis.call('PEXPIREAT', KEYS[1], ARGV[2])
            end
            return {next, 1}
            """, List.class);

    private final StringRedisTemplate redis;

    public LuaFixedWindowRateLimitCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitCounterResult increment(String key, long upperBound, Instant windowEnd) {
        if (upperBound < 1) {
            throw new IllegalArgumentException("upperBound must be positive");
        }
        List<?> reply = redis.execute(
                SCRIPT,
                List.of(key),
                Long.toString(upperBound),
                Long.toString(windowEnd.toEpochMilli())
        );
        return RedisCommandReply.rateLimitResult(reply, windowEnd);
    }
}

