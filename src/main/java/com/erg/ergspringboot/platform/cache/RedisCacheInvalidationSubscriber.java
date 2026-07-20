package com.erg.ergspringboot.platform.cache;

import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.databind.ObjectMapper;

public final class RedisCacheInvalidationSubscriber implements MessageListener {

    private final ObjectMapper mapper;
    private final RedisCacheGenerationService generations;
    private final ErgTieredCacheManager cacheManager;

    public RedisCacheInvalidationSubscriber(
            ObjectMapper mapper,
            RedisCacheGenerationService generations,
            ErgTieredCacheManager cacheManager
    ) {
        this.mapper = mapper;
        this.generations = generations;
        this.cacheManager = cacheManager;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            CacheInvalidationMessage invalidation = mapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), CacheInvalidationMessage.class);
            if (invalidation.schemaVersion() != 1 || invalidation.generation() < 0) return;
            generations.acceptRemote(invalidation.tenantId(), invalidation.cacheName(), invalidation.generation());
            cacheManager.clearLocal(invalidation.cacheName(), invalidation.tenantId());
        } catch (RuntimeException ignored) {
            // Malformed or future-version messages are ignored; bounded L1 TTL is the safety net.
        }
    }
}

