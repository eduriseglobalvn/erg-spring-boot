package com.erg.ergspringboot.platform.cache;

import java.time.Duration;

public final class NoOpRedisCacheStore implements RedisCacheStore {
    public static final NoOpRedisCacheStore INSTANCE = new NoOpRedisCacheStore();

    private NoOpRedisCacheStore() {
    }

    @Override
    public byte[] get(String key) {
        return null;
    }

    @Override
    public void put(String key, byte[] value, Duration ttl) {
    }

    @Override
    public void evict(String key) {
    }
}

