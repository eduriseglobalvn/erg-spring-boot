package com.erg.ergspringboot.platform.cache;

import java.time.Duration;

public interface RedisCacheStore {
    byte[] get(String key);

    void put(String key, byte[] value, Duration ttl);

    void evict(String key);
}

