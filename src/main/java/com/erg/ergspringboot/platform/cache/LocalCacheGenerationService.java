package com.erg.ergspringboot.platform.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class LocalCacheGenerationService implements CacheGenerationService {

    // shortcut: unbounded, one entry per tenant x cache x invalidated scope (teacher/assignment/quiz/student), so it
    // grows with data size, not traffic; it must not evict (a lost counter would resurrect stale L2 keys). Move
    // to Redis state (RedisCacheGenerationService) before one instance serves many large tenants.
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

