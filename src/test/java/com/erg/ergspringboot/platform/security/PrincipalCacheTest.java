package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.platform.cache.CacheInvalidationService;
import com.erg.ergspringboot.platform.cache.LocalCacheGenerationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrincipalCacheTest {

    private final LocalCacheGenerationService generations = new LocalCacheGenerationService();
    private final PrincipalCache cache = new PrincipalCache(generations,
            new CacheInvalidationService(generations, ignored -> { }));

    @Test
    void returnsAStoredPrincipalUntilTheUserIsEvicted() {
        cache.put("hash-a", principal("user-a"), Instant.now().plusSeconds(60), cache.epoch());
        cache.put("hash-b", principal("user-b"), Instant.now().plusSeconds(60), cache.epoch());

        cache.evictUser("user-a");

        assertThat(cache.get("hash-a")).isEmpty();
        assertThat(cache.get("hash-b")).isPresent();
    }

    @Test
    void doesNotStoreALoadThatOverlappedAnInvalidation() {
        long epochBeforeLoad = cache.epoch();
        cache.evictUser("user-a");

        cache.put("hash-a", principal("user-a"), Instant.now().plusSeconds(60), epochBeforeLoad);

        assertThat(cache.get("hash-a")).isEmpty();
    }

    @Test
    void neverOutlivesTheAccessToken() {
        cache.put("hash-a", principal("user-a"), Instant.now().minusMillis(1), cache.epoch());

        assertThat(cache.get("hash-a")).isEmpty();
    }

    @Test
    void anotherInstanceInvalidationRejectsTheEntry() {
        cache.put("hash-a", principal("user-a"), Instant.now().plusSeconds(60), cache.epoch());

        // What RedisCacheGenerationService.acceptRemote does when another instance evicts this user.
        generations.increment("principal", "ergPrincipal#user-a");

        assertThat(cache.get("hash-a")).isEmpty();
    }

    @Test
    void roleDefinitionChangeDropsEverything() {
        cache.put("hash-a", principal("user-a"), Instant.now().plusSeconds(60), cache.epoch());

        cache.evictAll();

        assertThat(cache.get("hash-a")).isEmpty();
    }

    @Test
    void entryStaysValidForItsTtlWhenTheGenerationStoreIsUnreachable() {
        java.util.concurrent.atomic.AtomicBoolean down = new java.util.concurrent.atomic.AtomicBoolean();
        com.erg.ergspringboot.platform.cache.CacheGenerationService flaky =
                new com.erg.ergspringboot.platform.cache.CacheGenerationService() {
                    public long current(String tenantId, String cacheName) {
                        return down.get() ? -1 : generations.current(tenantId, cacheName);
                    }

                    public long increment(String tenantId, String cacheName) {
                        return generations.increment(tenantId, cacheName);
                    }
                };
        PrincipalCache flakyCache = new PrincipalCache(flaky, new CacheInvalidationService(generations, ignored -> { }));
        flakyCache.put("hash-a", principal("user-a"), Instant.now().plusSeconds(60), flakyCache.epoch());

        down.set(true);

        assertThat(flakyCache.get("hash-a")).isPresent();
    }

    @Test
    void evictingOneUserDropsAllHisTokensAndKeepsOthers() {
        cache.put("hash-a1", principal("user-a"), Instant.now().plusSeconds(60), cache.epoch());
        cache.put("hash-a2", principal("user-a"), Instant.now().plusSeconds(60), cache.epoch());
        cache.put("hash-b", principal("user-b"), Instant.now().plusSeconds(60), cache.epoch());

        cache.evictUser("user-a");

        assertThat(cache.get("hash-a1")).isEmpty();
        assertThat(cache.get("hash-a2")).isEmpty();
        assertThat(cache.get("hash-b")).isPresent();
    }

    @Test
    void anotherUsersEvictionDoesNotBlockStoringALoadInFlight() {
        long epochBeforeLoad = cache.epoch();
        cache.evictUser("user-a");

        cache.put("hash-b", principal("user-b"), Instant.now().plusSeconds(60), epochBeforeLoad);

        assertThat(cache.get("hash-b")).isPresent();
    }

    @Test
    void aLoadStartedAfterTheEvictionIsStored() {
        cache.evictUser("user-a");

        cache.put("hash-a", principal("user-a"), Instant.now().plusSeconds(60), cache.epoch());

        assertThat(cache.get("hash-a")).isPresent();
    }

    @Test
    void roleDefinitionChangeRefusesLoadsInFlightForAnyUser() {
        long epochBeforeLoad = cache.epoch();
        cache.evictAll();

        cache.put("hash-b", principal("user-b"), Instant.now().plusSeconds(60), epochBeforeLoad);

        assertThat(cache.get("hash-b")).isEmpty();
    }

    @Test
    void anotherInstanceRoleDefinitionChangeRejectsEveryEntry() {
        cache.put("hash-a", principal("user-a"), Instant.now().plusSeconds(60), cache.epoch());

        // What RedisCacheGenerationService.acceptRemote does when another instance calls evictAll.
        generations.increment("principal", "ergPrincipal#*all*");

        assertThat(cache.get("hash-a")).isEmpty();
    }

    @Test
    void entryIsServedOnlyForThirtySecondsWhileTheGenerationStoreIsUnreachable() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        java.util.concurrent.atomic.AtomicBoolean down = new java.util.concurrent.atomic.AtomicBoolean();
        com.erg.ergspringboot.platform.cache.CacheGenerationService flaky =
                new com.erg.ergspringboot.platform.cache.CacheGenerationService() {
                    public long current(String tenantId, String cacheName) {
                        return down.get() ? -1 : generations.current(tenantId, cacheName);
                    }

                    public long increment(String tenantId, String cacheName) {
                        return generations.increment(tenantId, cacheName);
                    }
                };
        PrincipalCache outageCache = new PrincipalCache(flaky, new CacheInvalidationService(generations, ignored -> { }), clock);
        outageCache.put("hash-a", principal("user-a"), now.get().plusSeconds(600), outageCache.epoch());

        now.set(now.get().plus(Duration.ofSeconds(90)));
        assertThat(outageCache.get("hash-a")).isPresent();

        down.set(true);
        assertThat(outageCache.get("hash-a")).isEmpty();
    }

    private static ErgPrincipal principal(String userId) {
        return ErgPrincipal.authenticated(userId, "session-" + userId, "erg", List.of("student"), List.of(),
                List.of(), List.of("elearning"), "erg", "user", Map.of());
    }
}
