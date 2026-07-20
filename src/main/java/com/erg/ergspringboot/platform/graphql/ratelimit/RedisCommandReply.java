package com.erg.ergspringboot.platform.graphql.ratelimit;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class RedisCommandReply {

    private RedisCommandReply() {
    }

    static RateLimitCounterResult rateLimitResult(Object raw, java.time.Instant expiresAt) {
        if (!(raw instanceof List<?> values) || values.size() != 2) {
            throw new IllegalStateException("Unexpected Redis rate-limit reply shape");
        }
        long value = number(values.get(0));
        long appliedIncrement = number(values.get(1));
        return new RateLimitCounterResult(value, appliedIncrement != 0, expiresAt);
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof byte[] bytes) {
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        throw new IllegalStateException("Unexpected numeric Redis reply");
    }
}
