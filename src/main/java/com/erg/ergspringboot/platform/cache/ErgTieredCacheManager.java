package com.erg.ergspringboot.platform.cache;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public final class ErgTieredCacheManager implements CacheManager {

    private final Map<String, Cache> caches;
    private final CacheDescriptorRegistry descriptors;

    public ErgTieredCacheManager(
            CacheDescriptorRegistry descriptors,
            CacheKeyFactory keys,
            CacheValueCodec codec,
            RedisCacheStore store,
            CacheTenantResolver tenants,
            CacheGenerationService generations,
            CacheFailurePolicy failures,
            CacheLoadCoordinator loads
    ) {
        this(descriptors, keys, codec, store, tenants, generations, failures, loads,
                descriptors.all().stream().map(CacheDescriptor::name).collect(java.util.stream.Collectors.toSet()), null);
    }

    public ErgTieredCacheManager(
            CacheDescriptorRegistry descriptors,
            CacheKeyFactory keys,
            CacheValueCodec codec,
            RedisCacheStore store,
            CacheTenantResolver tenants,
            CacheGenerationService generations,
            CacheFailurePolicy failures,
            CacheLoadCoordinator loads,
            Set<String> l2Names
    ) {
        this(descriptors, keys, codec, store, tenants, generations, failures, loads, l2Names, null);
    }

    public ErgTieredCacheManager(
            CacheDescriptorRegistry descriptors,
            CacheKeyFactory keys,
            CacheValueCodec codec,
            RedisCacheStore store,
            CacheTenantResolver tenants,
            CacheGenerationService generations,
            CacheFailurePolicy failures,
            CacheLoadCoordinator loads,
            Set<String> l2Names,
            CacheInvalidationService invalidations
    ) {
        this(descriptors, keys, codec, store, tenants, generations, failures, loads, l2Names, invalidations, null);
    }

    public ErgTieredCacheManager(
            CacheDescriptorRegistry descriptors,
            CacheKeyFactory keys,
            CacheValueCodec codec,
            RedisCacheStore store,
            CacheTenantResolver tenants,
            CacheGenerationService generations,
            CacheFailurePolicy failures,
            CacheLoadCoordinator loads,
            Set<String> l2Names,
            CacheInvalidationService invalidations,
            CacheMetrics metrics
    ) {
        this.descriptors = descriptors;
        Map<String, Cache> built = new LinkedHashMap<>();
        for (CacheDescriptor<?> descriptor : descriptors.all()) {
            RedisCacheStore selectedStore = l2Names.contains(descriptor.name())
                    ? store : NoOpRedisCacheStore.INSTANCE;
            if (l2Names.contains(descriptor.name()) && descriptor.valueType().getRawClass() == Object.class) {
                throw new IllegalArgumentException("L2 cache requires an explicit value type: " + descriptor.name());
            }
            built.put(descriptor.name(), new ErgTieredCache(descriptor, keys, codec, selectedStore,
                    tenants, generations, failures, loads, invalidations, metrics));
        }
        for (String l2Name : l2Names) {
            if (!built.containsKey(l2Name)) {
                throw new IllegalArgumentException("Unknown L2 cache name: " + l2Name);
            }
        }
        this.caches = Map.copyOf(built);
    }

    @Override
    public Cache getCache(String name) {
        return caches.get(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        return caches.keySet();
    }

    public CacheDescriptor<?> descriptor(String cacheName) {
        return descriptors.require(cacheName);
    }

    public void clearLocal(String cacheName, String tenantId) {
        Cache cache = caches.get(cacheName);
        if (cache instanceof ErgTieredCache tiered) {
            tiered.clearLocal(tenantId);
        }
    }
}
