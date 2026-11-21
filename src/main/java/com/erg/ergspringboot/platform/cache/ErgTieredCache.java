package com.erg.ergspringboot.platform.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class ErgTieredCache implements Cache {

    // shortcut: fixed weight for L1-only entries, switch to measured size if entries vary a lot
    private static final int L1_ONLY_WEIGHT = 1024;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ErgTieredCache.class);

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
    private final boolean l2Enabled;
    // Weight of the entry being put when the caller already knows it (encoded length); read by the weigher on this thread.
    private final ThreadLocal<Integer> knownWeight = new ThreadLocal<>();

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
        this.l2Enabled = store != NoOpRedisCacheStore.INSTANCE;
        this.l1 = Caffeine.newBuilder()
                .maximumWeight(descriptor.l1MaximumWeightBytes())
                .weigher((ScopedCacheKey key, Object value) -> weigh(value))
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
                // Read before the loader: an invalidation that lands while it runs makes the loaded value stale.
                long generationBefore = generation(scoped, key);
                T loaded = valueLoader.call();
                if (loaded == null && !descriptor.cacheNulls()) return loaded;
                if (generation(scoped, key) == generationBefore) {
                    put(key, loaded);
                    // An invalidation between the check and the put would leave a stale L1 entry: take it back out.
                    if (generation(scoped, key) != generationBefore) l1.invalidate(scoped);
                } else {
                    metric("l1", "put", "stale_skipped");
                }
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
        String redisKey = redisKey(scoped, key);
        if (redisKey == null) {
            l1Put(scoped, value, null);
            return;
        }
        long started = System.nanoTime();
        CacheEncoding encoded;
        try {
            encoded = codec.encode(descriptor, value);
        } catch (RuntimeException exception) {
            l1Put(scoped, value, 1);
            // L2 is an optimisation: the value is already loaded and in L1, so the request must not fail.
            metric("l2", "put", "encode_error");
            LOG.warn("Skipping L2 put for cache {}: {}", descriptor.name(), exception.toString());
            return;
        }
        metricSerialization(encoded.serializedSize(), System.nanoTime() - started);
        l1Put(scoped, value, Math.max(1, encoded.serializedSize()));
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
        // Generation first, L1 second: a reader slipping in between sees the new generation and discards its stale load.
        if (invalidations == null) {
            generations.increment(tenant, descriptor.name());
        } else {
            invalidations.clearTenantCache(tenant, descriptor.name());
        }
        clearLocal(tenant);
        // A reader between this clear and the commit can re-fill L1 with pre-commit data that the
        // post-commit generation bump never reaches (L1 keys carry no generation), so clear again then.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clearLocal(tenant);
                }
            });
        }
        metric("coordination", "invalidate", "generation");
    }

    public void clearLocal() {
        l1.invalidateAll();
    }

    public void clearLocal(String tenantId) {
        l1.asMap().keySet().removeIf(key -> Objects.equals(key.tenantId(), tenantId));
    }

    /**
     * Drops this tenant's L1 entries of one scope (and tenant-less ones: no tenant context cached them);
     * L2 entries become unreachable through the scope generation.
     */
    public void clearLocal(String tenantId, String scope) {
        l1.asMap().keySet().removeIf(key -> (key.tenantId() == null || Objects.equals(key.tenantId(), tenantId))
                && Objects.equals(scope, descriptor.scopeFor(key.sourceKey())));
    }

    /** Generation name of one scope; '#' cannot occur in a cache name, so it never collides with one. */
    public static String scopeGenerationName(String cacheName, String scope) {
        return cacheName + "#" + scope;
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
            if (decoded != null) l1Put(scoped, decoded, Math.max(1, bytes.length));
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
        if (!l2Enabled) return null;
        long generation = generation(scoped, sourceKey);
        if (generation < 0) return null;
        String tenant = scoped.tenantId();
        return keys.businessKey(tenant == null ? "global" : tenant, descriptor, sourceKey, generation);
    }

    /** Combined tenant and scope generation of a key, or -1 when it cannot be read (no tenant, or generations unavailable). */
    private long generation(ScopedCacheKey scoped, Object sourceKey) {
        String tenant = scoped.tenantId();
        if (descriptor.tenantRequired() && tenant == null) return -1;
        String effectiveTenant = tenant == null ? "global" : tenant;
        long generation = generations.current(effectiveTenant, descriptor.name());
        if (generation < 0) return -1;
        String scope = descriptor.scopeFor(sourceKey);
        if (scope != null) {
            long scopeGeneration = generations.current(effectiveTenant, scopeGenerationName(descriptor.name(), scope));
            if (scopeGeneration < 0) return -1;
            // Either a tenant-wide clear or a clear of this scope moves the key; both counters stay far below 2^31.
            generation = (generation << 32) | (scopeGeneration & 0xFFFFFFFFL);
        }
        return generation;
    }

    private String tenantId() {
        String tenant = tenants.currentTenantId();
        return tenant == null || tenant.isBlank() ? null : tenant.trim();
    }

    private void l1Put(ScopedCacheKey scoped, Object value, Integer weight) {
        knownWeight.set(weight);
        try {
            l1.put(scoped, value);
        } finally {
            knownWeight.remove();
        }
    }

    private int weigh(Object value) {
        Integer known = knownWeight.get();
        if (known != null) return known;
        if (!l2Enabled) return L1_ONLY_WEIGHT;
        try {
            return Math.max(1, codec.encode(descriptor, value).serializedSize());
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
