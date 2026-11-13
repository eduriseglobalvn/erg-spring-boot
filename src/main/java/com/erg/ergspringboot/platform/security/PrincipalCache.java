package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.platform.cache.CacheGenerationService;
import com.erg.ergspringboot.platform.cache.CacheInvalidationService;
import com.erg.ergspringboot.platform.cache.ErgTieredCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Bounded in-process cache of authenticated principals keyed by the access-token hash (PERF-01).
 * Policy (TTL, size, eviction triggers): docs/architecture/module-cache-descriptors.md, "Principal cache".
 */
@Component
public class PrincipalCache {

    static final String CACHE_NAME = "ergPrincipal";
    static final Duration TTL = Duration.ofSeconds(30);
    // User ids are global primary keys, so per-user generations share one namespace instead of a tenant.
    private static final String GENERATION_NAMESPACE = "principal";

    private final Cache<String, Entry> entries = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(10_000)
            .build();
    // Bumped by every local invalidation; a load that overlapped one is not stored (see put).
    private final AtomicLong epoch = new AtomicLong();
    private final CacheGenerationService generations;
    private final CacheInvalidationService invalidations;

    public PrincipalCache(CacheGenerationService generations, CacheInvalidationService invalidations) {
        this.generations = generations;
        this.invalidations = invalidations;
    }

    Optional<ErgPrincipal> get(String tokenHash) {
        Entry entry = entries.getIfPresent(tokenHash);
        if (entry == null) {
            return Optional.empty();
        }
        if (!Instant.now().isBefore(entry.validUntil()) || entry.generation() != generation(entry.principal().userId())) {
            entries.invalidate(tokenHash);
            return Optional.empty();
        }
        return Optional.of(entry.principal());
    }

    long epoch() {
        return epoch.get();
    }

    /** Stores a successfully loaded principal unless an invalidation committed since {@code epochBeforeLoad}. */
    void put(String tokenHash, ErgPrincipal principal, Instant validUntil, long epochBeforeLoad) {
        long generation = generation(principal.userId());
        if (generation < 0 || epoch.get() != epochBeforeLoad) {
            return;
        }
        entries.put(tokenHash, new Entry(principal, validUntil, generation));
        // An invalidation may have bumped the epoch between the check and the put; it then also clears,
        // but only if its clear ran after this put, so drop the entry here too.
        if (epoch.get() != epochBeforeLoad) {
            entries.invalidate(tokenHash);
        }
    }

    /** Drops every cached principal of this user after the current transaction commits, on all instances. */
    public void evictUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        afterCommit(() -> {
            epoch.incrementAndGet();
            entries.asMap().values().removeIf(entry -> userId.equals(entry.principal().userId()));
        });
        invalidations.clearTenantCacheScope(GENERATION_NAMESPACE, CACHE_NAME, userId);
    }

    /** Role/permission definition changed: drop this instance's cache; other instances expire within the TTL. */
    public void evictAll() {
        afterCommit(() -> {
            epoch.incrementAndGet();
            entries.invalidateAll();
        });
    }

    private long generation(String userId) {
        return generations.current(GENERATION_NAMESPACE, ErgTieredCache.scopeGenerationName(CACHE_NAME, userId));
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record Entry(ErgPrincipal principal, Instant validUntil, long generation) {
    }
}
