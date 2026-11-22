package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.platform.cache.CacheGenerationService;
import com.erg.ergspringboot.platform.cache.CacheInvalidationService;
import com.erg.ergspringboot.platform.cache.ErgTieredCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    // Revocations reach other instances through the generation counters, so a long TTL only bounds inputs they do not cover.
    static final Duration TTL = Duration.ofSeconds(120);
    // While the generation store (Redis) is unreachable a cached principal is trusted only this long.
    static final Duration OUTAGE_TTL = Duration.ofSeconds(30);
    // User ids are global primary keys, so per-user generations share one namespace instead of a tenant.
    private static final String GENERATION_NAMESPACE = "principal";
    private static final String ALL_SCOPE = "*all*";

    // Monotonic sequence of local invalidations; a load snapshots it (epoch()) and put refuses if its user was evicted since.
    private final AtomicLong epoch = new AtomicLong();
    // Highest sequence at which every load in flight must be refused: evictAll, or a per-user marker dropped by size pressure.
    private final AtomicLong floor = new AtomicLong();
    private final Cache<String, Long> userEvictions = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .executor(Runnable::run)
            .removalListener((String user, Long seq, RemovalCause cause) -> {
                if (cause == RemovalCause.SIZE && seq != null) {
                    floor.accumulateAndGet(seq, Math::max);
                }
            })
            .build();
    // userId -> token hashes; entries may go stale (the removal listener cleans them), so eviction re-checks via invalidate.
    private final ConcurrentHashMap<String, Set<String>> tokensByUser = new ConcurrentHashMap<>();
    private final Cache<String, Entry> entries = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(25_000)
            .executor(Runnable::run)
            .removalListener((String tokenHash, Entry entry, RemovalCause cause) -> {
                if (entry != null && cause != RemovalCause.REPLACED) {
                    tokensByUser.computeIfPresent(entry.principal().userId(), (user, tokens) -> {
                        tokens.remove(tokenHash);
                        return tokens.isEmpty() ? null : tokens;
                    });
                }
            })
            .build();
    private final CacheGenerationService generations;
    private final CacheInvalidationService invalidations;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PrincipalCache(CacheGenerationService generations, CacheInvalidationService invalidations) {
        this(generations, invalidations, Clock.systemUTC());
    }

    PrincipalCache(CacheGenerationService generations, CacheInvalidationService invalidations, Clock clock) {
        this.generations = generations;
        this.invalidations = invalidations;
        this.clock = clock;
    }

    Optional<ErgPrincipal> get(String tokenHash) {
        Entry entry = entries.getIfPresent(tokenHash);
        if (entry == null) {
            return Optional.empty();
        }
        // A negative generation means the generation store (Redis) is unreachable: serve the entry only for OUTAGE_TTL.
        long current = generation(entry.principal().userId());
        long all = allGeneration();
        Instant now = clock.instant();
        boolean stale = (current >= 0 && entry.generation() != current) || (all >= 0 && entry.allGeneration() != all)
                || ((current < 0 || all < 0) && now.isAfter(entry.createdAt().plus(OUTAGE_TTL)));
        if (!now.isBefore(entry.validUntil()) || stale) {
            entries.invalidate(tokenHash);
            return Optional.empty();
        }
        return Optional.of(entry.principal());
    }

    long epoch() {
        return epoch.get();
    }

    /** Stores a successfully loaded principal unless this user (or everyone) was invalidated since {@code epochBeforeLoad}. */
    void put(String tokenHash, ErgPrincipal principal, Instant validUntil, long epochBeforeLoad) {
        String userId = principal.userId();
        long generation = generation(userId);
        long all = allGeneration();
        if (generation < 0 || all < 0 || overlapped(userId, epochBeforeLoad)) {
            return;
        }
        entries.put(tokenHash, new Entry(principal, validUntil, generation, all, clock.instant()));
        tokensByUser.compute(userId, (user, tokens) -> {
            Set<String> updated = tokens == null ? ConcurrentHashMap.newKeySet() : tokens;
            updated.add(tokenHash);
            return updated;
        });
        // An eviction may have marked the user between the check and the put and removed the index before it held this
        // token, so drop the entry here too.
        if (overlapped(userId, epochBeforeLoad)) {
            entries.invalidate(tokenHash);
        }
    }

    private boolean overlapped(String userId, long epochBeforeLoad) {
        Long evictedAt = userEvictions.getIfPresent(userId);
        return epochBeforeLoad < floor.get() || (evictedAt != null && epochBeforeLoad < evictedAt);
    }

    /** Drops every cached principal of this user after the current transaction commits, on all instances. */
    public void evictUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        afterCommit(() -> {
            userEvictions.put(userId, epoch.incrementAndGet());
            Set<String> tokens = tokensByUser.get(userId);
            if (tokens != null) {
                tokens.forEach(entries::invalidate);
            }
        });
        invalidations.clearTenantCacheScope(GENERATION_NAMESPACE, CACHE_NAME, userId);
    }

    /** Role/permission definition changed: drop every cached principal here and, through one shared generation, on other instances. */
    public void evictAll() {
        afterCommit(() -> {
            floor.accumulateAndGet(epoch.incrementAndGet(), Math::max);
            entries.invalidateAll();
        });
        invalidations.clearTenantCacheScope(GENERATION_NAMESPACE, CACHE_NAME, ALL_SCOPE);
    }

    private long generation(String userId) {
        return generations.current(GENERATION_NAMESPACE, ErgTieredCache.scopeGenerationName(CACHE_NAME, userId));
    }

    private long allGeneration() {
        return generation(ALL_SCOPE);
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

    private record Entry(ErgPrincipal principal, Instant validUntil, long generation, long allGeneration, Instant createdAt) {
    }
}
