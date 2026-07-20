package com.erg.ergspringboot.platform.graphql.ratelimit;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.data.redis.connection.RedisConnection;

final class LettuceNativeAccess {

    private LettuceNativeAccess() {
    }

    @SuppressWarnings("unchecked")
    static RedisCommands<byte[], byte[]> commands(RedisConnection connection) {
        Object nativeConnection = connection.getNativeConnection();
        StatefulRedisConnection<?, ?> stateful;
        if (nativeConnection instanceof StatefulRedisConnection<?, ?> directConnection) {
            stateful = directConnection;
        } else if (nativeConnection instanceof RedisAsyncCommands<?, ?> asyncCommands) {
            stateful = asyncCommands.getStatefulConnection();
        } else {
            throw new IllegalStateException("Unsupported Lettuce native connection: "
                    + nativeConnection.getClass().getName());
        }
        return ((StatefulRedisConnection<byte[], byte[]>) stateful).sync();
    }
}
