package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class ErgTieredCacheTest {

    @Test
    void fillsL2AndL1ThenReloadsL1FromL2() throws Exception {
        Fixture fixture = new Fixture();
        AtomicInteger loads = new AtomicInteger();

        assertThat(fixture.cache.get("student-1", () -> {
            loads.incrementAndGet();
            return "dashboard";
        })).isEqualTo("dashboard");
        assertThat(fixture.cache.get("student-1", String.class)).isEqualTo("dashboard");

        fixture.cache.clearLocal();

        assertThat(fixture.cache.get("student-1", String.class)).isEqualTo("dashboard");
        assertThat(loads).hasValue(1);
        assertThat(fixture.store.gets).isEqualTo(3);
    }

    @Test
    void cacheStoreFailureFallsBackToLoaderAndKeepsL1Useful() throws Exception {
        Fixture fixture = new Fixture();
        fixture.store.fail = true;
        AtomicInteger loads = new AtomicInteger();

        assertThat(fixture.cache.get("student-1", () -> {
            loads.incrementAndGet();
            return "database-value";
        })).isEqualTo("database-value");
        assertThat(fixture.cache.get("student-1", String.class)).isEqualTo("database-value");
        assertThat(loads).hasValue(1);
    }

    @Test
    void clearInsideTransactionDropsL1EntriesRefilledBeforeCommit() {
        Fixture fixture = new Fixture();
        TransactionSynchronizationManager.initSynchronization();
        try {
            fixture.cache.clear();
            fixture.cache.put("student-1", "pre-commit-value");

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            assertThat(((com.github.benmanes.caffeine.cache.Cache<?, ?>) fixture.cache.getNativeCache()).asMap())
                    .isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void clearingOneScopeKeepsOtherScopesInL1AndL2() {
        Fixture fixture = new Fixture(key -> ((List<?>) key).get(1).toString());
        List<String> assignmentA = List.of("tenant-a", "asg-A");
        List<String> assignmentB = List.of("tenant-a", "asg-B");
        fixture.cache.put(assignmentA, "progress-A");
        fixture.cache.put(assignmentB, "progress-B");

        // What CacheInvalidationService.clearTenantCacheScope + the after-commit local clear do for asg-A.
        fixture.generations.increment("tenant-a", ErgTieredCache.scopeGenerationName("studentDashboard", "asg-A"));
        fixture.cache.clearLocal("tenant-a", "asg-A");

        assertThat(fixture.cache.get(assignmentA, String.class)).isNull();
        assertThat(fixture.cache.get(assignmentB, String.class)).isEqualTo("progress-B");
        fixture.cache.clearLocal();
        assertThat(fixture.cache.get(assignmentB, String.class)).as("asg-B still served from L2").isEqualTo("progress-B");
        assertThat(fixture.cache.get(assignmentA, String.class)).as("asg-A L2 key moved with its scope generation").isNull();
    }

    @Test
    void tenantWideClearStillInvalidatesScopedKeys() {
        Fixture fixture = new Fixture(key -> ((List<?>) key).get(1).toString());
        List<String> assignmentA = List.of("tenant-a", "asg-A");
        fixture.cache.put(assignmentA, "progress-A");

        fixture.generations.increment("tenant-a", "studentDashboard");
        fixture.cache.clearLocal("tenant-a");

        assertThat(fixture.cache.get(assignmentA, String.class)).isNull();
    }

    private static final class Fixture {
        private final JsonMapper mapper = JsonMapper.builder().build();
        private final CacheDescriptor<String> descriptor;
        private final FakeStore store = new FakeStore();
        private final LocalCacheGenerationService generations = new LocalCacheGenerationService();
        private final ErgTieredCache cache;

        Fixture() {
            this(null);
        }

        Fixture(java.util.function.Function<Object, String> scope) {
            descriptor = new CacheDescriptor<>("studentDashboard", mapper.constructType(String.class), Duration.ofMinutes(1),
                    Duration.ofMinutes(2), 1024 * 1024, 1024, true, false, scope);
            cache = new ErgTieredCache(
                descriptor,
                new CacheKeyFactory(mapper),
                new JacksonCacheValueCodec(mapper),
                store,
                () -> "tenant-a",
                generations,
                new CacheFailurePolicy(2, Duration.ofSeconds(1)),
                new CacheLoadCoordinator()
            );
        }
    }

    private static final class FakeStore implements RedisCacheStore {
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();
        private int gets;
        private boolean fail;

        @Override
        public byte[] get(String key) {
            gets++;
            if (fail) throw new IllegalStateException("Redis unavailable");
            return values.get(key);
        }

        @Override
        public void put(String key, byte[] value, Duration ttl) {
            if (fail) throw new IllegalStateException("Redis unavailable");
            values.put(key, value);
        }

        @Override
        public void evict(String key) {
            values.remove(key);
        }
    }
}
