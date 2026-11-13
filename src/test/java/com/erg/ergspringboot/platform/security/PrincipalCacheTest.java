package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.platform.cache.CacheInvalidationService;
import com.erg.ergspringboot.platform.cache.LocalCacheGenerationService;
import java.time.Instant;
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

    private static ErgPrincipal principal(String userId) {
        return ErgPrincipal.authenticated(userId, "session-" + userId, "erg", List.of("student"), List.of(),
                List.of(), List.of("elearning"), "erg", "user", Map.of());
    }
}
