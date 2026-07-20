package com.erg.ergspringboot.platform.redis;

import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

final class RedisClientFactory {

    private RedisClientFactory() {
    }

    static LettuceConnectionFactory create(ErgRedisProperties.Endpoint properties) {
        RedisURI uri = RedisURI.create(properties.getUrl());
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(uri.getHost(), uri.getPort());
        server.setDatabase(uri.getDatabase());
        if (uri.getUsername() != null && !uri.getUsername().isBlank()) {
            server.setUsername(uri.getUsername());
        }
        char[] password = uri.getPassword();
        if (password != null && password.length > 0) {
            server.setPassword(RedisPassword.of(password));
        }

        GenericObjectPoolConfig<StatefulConnection<?, ?>> pool = new GenericObjectPoolConfig<>();
        pool.setMaxTotal(properties.getPoolSize());
        pool.setMaxIdle(properties.getPoolSize());
        pool.setMinIdle(Math.min(properties.getMinIdle(), properties.getPoolSize()));
        pool.setMaxWait(properties.getConnectTimeout());
        pool.setTestOnBorrow(true);

        LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder builder =
                LettucePoolingClientConfiguration.builder()
                        .poolConfig(pool)
                        .commandTimeout(properties.getCommandTimeout())
                        .clientOptions(io.lettuce.core.ClientOptions.builder()
                                .socketOptions(SocketOptions.builder()
                                        .connectTimeout(properties.getConnectTimeout())
                                        .build())
                                .build())
                        .shutdownTimeout(properties.getConnectTimeout());
        if (uri.isSsl()) {
            builder.useSsl();
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(server, builder.build());
        factory.setValidateConnection(true);
        factory.setShareNativeConnection(true);
        return factory;
    }
}
