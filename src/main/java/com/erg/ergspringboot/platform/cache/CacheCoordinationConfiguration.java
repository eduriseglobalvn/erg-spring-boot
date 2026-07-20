package com.erg.ergspringboot.platform.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class CacheCoordinationConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "erg.redis.state", name = "enabled", havingValue = "false", matchIfMissing = true)
    CacheGenerationService localCacheGenerationService() {
        return new LocalCacheGenerationService();
    }

    @Bean
    @ConditionalOnProperty(prefix = "erg.redis.state", name = "enabled", havingValue = "false", matchIfMissing = true)
    CacheInvalidationPublisher localCacheInvalidationPublisher() {
        return ignored -> { };
    }

    @Bean
    @ConditionalOnProperty(prefix = "erg.redis.state", name = "enabled", havingValue = "true")
    RedisCacheGenerationService redisCacheGenerationService(
            @Qualifier("stateStringRedisTemplate") StringRedisTemplate redis
    ) {
        return new RedisCacheGenerationService(redis);
    }

    @Bean
    @ConditionalOnProperty(prefix = "erg.redis.state", name = "enabled", havingValue = "true")
    CacheInvalidationPublisher redisCacheInvalidationPublisher(
            @Qualifier("stateStringRedisTemplate") StringRedisTemplate redis,
            ObjectMapper mapper
    ) {
        return new RedisCacheInvalidationPublisher(redis, mapper);
    }

    @Bean
    CacheInvalidationService cacheInvalidationService(
            CacheGenerationService generations,
            CacheInvalidationPublisher publisher
    ) {
        return new CacheInvalidationService(generations, publisher);
    }

    @Bean
    @ConditionalOnProperty(prefix = "erg.redis.state", name = "enabled", havingValue = "true")
    RedisMessageListenerContainer cacheInvalidationListenerContainer(
            @Qualifier("stateRedisConnectionFactory") RedisConnectionFactory connectionFactory,
            ObjectMapper mapper,
            RedisCacheGenerationService generations,
            ErgTieredCacheManager cacheManager
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                new RedisCacheInvalidationSubscriber(mapper, generations, cacheManager),
                new ChannelTopic(RedisCacheInvalidationPublisher.CHANNEL));
        return container;
    }
}

