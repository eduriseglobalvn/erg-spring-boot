package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
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

    private static final class Fixture {
        private final JsonMapper mapper = JsonMapper.builder().build();
        private final CacheDescriptor<String> descriptor = new CacheDescriptor<>(
                "studentDashboard", mapper.constructType(String.class), Duration.ofMinutes(1),
                Duration.ofMinutes(2), 1024 * 1024, 1024, true, false);
        private final FakeStore store = new FakeStore();
        private final LocalCacheGenerationService generations = new LocalCacheGenerationService();
        private final ErgTieredCache cache = new ErgTieredCache(
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
