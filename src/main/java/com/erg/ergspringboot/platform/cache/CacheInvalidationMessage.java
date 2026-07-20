package com.erg.ergspringboot.platform.cache;

import java.time.Instant;
import java.util.UUID;

public record CacheInvalidationMessage(
        int schemaVersion,
        String eventId,
        String sourceInstanceId,
        String tenantId,
        String cacheName,
        long generation,
        Instant occurredAt
) {
    public static CacheInvalidationMessage generation(
            String sourceInstanceId,
            String tenantId,
            String cacheName,
            long generation
    ) {
        return new CacheInvalidationMessage(1, UUID.randomUUID().toString(), sourceInstanceId,
                tenantId, cacheName, generation, Instant.now());
    }
}

