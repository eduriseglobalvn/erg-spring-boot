package com.erg.ergspringboot.platform.graphql.ratelimit;

import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.ArrayOutput;
import io.lettuce.core.protocol.CommandArgs;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class Redis88IncrexRateLimitCounter implements GraphQlRateLimitCounter {

    private final StringRedisTemplate redis;

    public Redis88IncrexRateLimitCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitCounterResult increment(String key, long upperBound, Instant windowEnd) {
        if (upperBound < 1) {
            throw new IllegalArgumentException("upperBound must be positive");
        }
        if (!windowEnd.isAfter(Instant.now())) {
            throw new IllegalArgumentException("windowEnd must be in the future");
        }
        Object reply = redis.execute((RedisCallback<Object>) connection -> {
            CommandArgs<byte[], byte[]> args = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                    .addKey(bytes(key))
                    .add("BYINT")
                    .add(1)
                    .add("UBOUND")
                    .add(upperBound)
                    .add("PXAT")
                    .add(windowEnd.toEpochMilli())
                    .add("ENX");
            return LettuceNativeAccess.commands(connection).dispatch(
                    Redis88Command.INCREX,
                    new ArrayOutput<>(ByteArrayCodec.INSTANCE),
                    args
            );
        });
        return RedisCommandReply.rateLimitResult(reply, windowEnd);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
