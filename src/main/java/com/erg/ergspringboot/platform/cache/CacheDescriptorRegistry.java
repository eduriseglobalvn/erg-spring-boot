package com.erg.ergspringboot.platform.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CacheDescriptorRegistry {

    private final Map<String, CacheDescriptor<?>> descriptors;

    public CacheDescriptorRegistry(Collection<? extends CacheDescriptor<?>> descriptors) {
        Map<String, CacheDescriptor<?>> byName = new LinkedHashMap<>();
        for (CacheDescriptor<?> descriptor : descriptors) {
            CacheDescriptor<?> previous = byName.putIfAbsent(descriptor.name(), descriptor);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate cache descriptor: " + descriptor.name());
            }
        }
        this.descriptors = Collections.unmodifiableMap(byName);
    }

    public CacheDescriptor<?> require(String cacheName) {
        CacheDescriptor<?> descriptor = descriptors.get(cacheName);
        if (descriptor == null) {
            throw new IllegalArgumentException("No typed cache descriptor registered for: " + cacheName);
        }
        return descriptor;
    }

    public Collection<CacheDescriptor<?>> all() {
        return descriptors.values();
    }
}

