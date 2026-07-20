package com.erg.ergspringboot.platform.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

public final class ErgTieredCache implements Cache {

    private final CacheDescriptor<Object> descriptor;
    private final CacheKeyFactory keys;
    private final CacheValueCodec codec;
    private final RedisCacheStore store;
    private final CacheTenantResolver tenants;
    private final CacheGenerationService generations;
    private final CacheFailurePolicy failures;
    private final CacheLoadCoordinator loads;
    private final CacheInvalidationService invalidations;
    private final CacheMetrics metrics;
    private final com.github.benmanes.caffeine.cache.Cache<ScopedCacheKey, Object> l1;

    @SuppressWarnings("unchecked")
    public ErgTieredCache(
            CacheDescriptor<?> descriptor,
            CacheKeyFactory keys,
            CacheValueCodec codec,
            RedisCacheStore store,
            CacheTenantResolver tenants,
            CacheGenerationService generations,
            CacheFailurePolicy failures,
            CacheLoadCoordinator loads
    ) {
        this(descriptor, keys, codec, store, tenants, generations, failures, loads, null, null);
    }

    public ErgTieredCache(
            CacheDescriptor<?> descriptor,
            CacheKeyFactory keys,
            CacheValueCodec codec,
            RedisCacheStore store,
            CacheTenantResolver tenants,
            CacheGenerationService generations,
            CacheFailurePolicy failures,
            CacheLoadCoordinator loads,
            CacheInvalidationService invalidations
    ) {
        this(descriptor, keys, codec, store, tenants, generations, failures, loads, invalidations, null);
    }

    public ErgTieredCache(
            CacheDescriptor<?> descriptor,
            CacheKeyFactory keys,
            CacheValueCodec codec,
            RedisCacheStore store,
            CacheTenantResolver tenants,
            CacheGenerationService generations,
            CacheFailurePolicy failures,
            CacheLoadCoordinator loads,
            CacheInvalidationService invalidations,
            CacheMetrics metrics
    ) {
        this.descriptor = (CacheDescriptor<Object>) descriptor;
        this.keys = keys;
        this.codec = codec;
        this.store = store;
        this.tenants = tenants;
        this.generations = generations;
        this.failures = failures;
        this.loads = loads;
        this.invalidations = invalidations;
        this.metrics = metrics;
        this.l1 = Caffeine.newBuilder()
                .maximumWeight(descriptor.l1MaximumWeightBytes())
                .weigher((ScopedCacheKey key, Object value) -> estimateWeight(value))
                .expireAfterWrite(descriptor.l1Ttl())
                .recordStats()
                .build();
    }

    @Override
    public String getName() {
        return descriptor.name();
    }

    @Override
    public Object getNativeCache() {
        return l1;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = lookup(key);
        return value == null ? null : new SimpleValueWrapper(value);
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        Object value = lookup(key);
        if (value == null) return null;
        if (type != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value for " + descriptor.name()
                    + " is not a " + type.getName());
        }
        return type.cast(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object existing = lookup(key);
        if (existing != null) return (T) existing;
        ScopedCacheKey scoped = scoped(key);
        try {
            return loads.load(new LoadKey(descriptor.name(), scoped), () -> {
                Object rechecked = lookup(key);
                if (rechecked != null) {
                    return (T) rechecked;
                }
                T loaded = valueLoader.call();
                if (loaded != null || descriptor.cacheNulls()) put(key, loaded);
                return loaded;
            });
        } catch (Exception exception) {
            throw new ValueRetrievalException(key, valueLoader, exception);
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null && !descriptor.cacheNulls()) return;
        ScopedCacheKey scoped = scoped(key);
        l1.put(scoped, value);
        String redisKey = redisKey(scoped, key);
        if (redisKey == null) return;
        long started = System.nanoTime();
        CacheEncoding encoded = codec.encode(descriptor, value);
        metricSerialization(encoded.serializedSize(), System.nanoTime() - started);
        if (encoded.cacheable()) {
            failures.execute(() -> store.put(redisKey, encoded.bytes(), descriptor.l2Ttl()));
            metric("l2", "put", "stored");
        } else {
            metric("l2", "put", "size_bypass");
        }
    }

    @Override
    public void evict(Object key) {
        ScopedCacheKey scoped = scoped(key);
        l1.invalidate(scoped);
        String redisKey = redisKey(scoped, key);
        if (redisKey != null) failures.execute(() -> store.evict(redisKey));
    }

    @Override
    public void clear() {
        String tenant = tenantId();
        if (tenant == null) {
            clearLocal();
            return;
        }
        clearLocal(tenant);
        if (invalidations == null) {
            generations.increment(tenant, descriptor.name());
        } else {
            invalidations.clearTenantCache(tenant, descriptor.name());
        }
        metric("coordination", "invalidate", "generation");
    }

    public void clearLocal() {
        l1.invalidateAll();
    }

    public void clearLocal(String tenantId) {
        l1.asMap().keySet().removeIf(key -> Objects.equals(key.tenantId(), tenantId));
    }

    private Object lookup(Object key) {
        ScopedCacheKey scoped = scoped(key);
        Object local = l1.getIfPresent(scoped);
        if (local != null) {
            metric("l1", "get", "hit");
            return local;
        }
        metric("l1", "get", "miss");
        String redisKey = redisKey(scoped, key);
        if (redisKey == null) return null;
        byte[] bytes = failures.execute(() -> store.get(redisKey), null);
        if (bytes == null) {
            metric("l2", "get", "miss");
            return null;
        }
        try {
            Object decoded = codec.decode(descriptor, bytes);
            if (decoded != null) l1.put(scoped, decoded);
            metric("l2", "get", "hit");
            return decoded;
        } catch (CacheValueDecodingException corruption) {
            failures.execute(() -> store.evict(redisKey));
            metric("l2", "get", "decode_failure");
            return null;
        }
    }

    private ScopedCacheKey scoped(Object sourceKey) {
        return new ScopedCacheKey(tenantId(), Objects.requireNonNull(sourceKey, "cache key"));
    }

    private String redisKey(ScopedCacheKey scoped, Object sourceKey) {
        String tenant = scoped.tenantId();
        if (descriptor.tenantRequired() && tenant == null) return null;
        String effectiveTenant = tenant == null ? "global" : tenant;
        long generation = generations.current(effectiveTenant, descriptor.name());
        if (generation < 0) return null;
        return keys.businessKey(effectiveTenant, descriptor, sourceKey, generation);
    }

    private String tenantId() {
        String tenant = tenants.currentTenantId();
        return tenant == null || tenant.isBlank() ? null : tenant.trim();
    }

    private int estimateWeight(Object value) {
        try {
            CacheEncoding encoded = codec.encode(descriptor, value);
            return Math.max(1, Math.min(Integer.MAX_VALUE, encoded.serializedSize()));
        } catch (RuntimeException ignored) {
            return 1;
        }
    }

    private void metric(String tier, String operation, String outcome) {
        if (metrics != null) metrics.record(descriptor.name(), tier, operation, outcome);
    }

    private void metricSerialization(int bytes, long nanos) {
        if (metrics != null) metrics.recordSerialization(descriptor.name(), bytes, nanos);
    }

    private record ScopedCacheKey(String tenantId, Object sourceKey) {
    }

    private record LoadKey(String cacheName, ScopedCacheKey key) {
    }
}
