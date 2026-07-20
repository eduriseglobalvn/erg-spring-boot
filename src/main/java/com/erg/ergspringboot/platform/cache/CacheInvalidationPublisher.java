package com.erg.ergspringboot.platform.cache;

@FunctionalInterface
public interface CacheInvalidationPublisher {
    void publish(CacheInvalidationMessage message);
}

