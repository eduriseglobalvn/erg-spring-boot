package com.erg.ergspringboot.platform.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class LocalCacheGenerationService implements CacheGenerationService {

    private final ConcurrentHashMap<String, AtomicLong> generations = new ConcurrentHashMap<>();

    @Override
    public long current(String tenantId, String cacheName) {
        AtomicLong generation = generations.get(key(tenantId, cacheName));
        return generation == null ? 0 : generation.get();
    }

    @Override
    public long increment(String tenantId, String cacheName) {
        return generations.computeIfAbsent(key(tenantId, cacheName), ignored -> new AtomicLong())
                .incrementAndGet();
    }

    private String key(String tenantId, String cacheName) {
        return tenantId + '\u001f' + cacheName;
    }
}

