package com.erg.ergspringboot.platform.cache;

public interface CacheGenerationService {
    long current(String tenantId, String cacheName);

    long increment(String tenantId, String cacheName);
}

