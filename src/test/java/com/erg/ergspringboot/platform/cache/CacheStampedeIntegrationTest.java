package com.erg.ergspringboot.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CacheStampedeIntegrationTest {

    @Test
    void oneProcessExecutesOneLoaderForConcurrentMisses() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        CacheDescriptor<String> descriptor = new CacheDescriptor<>("studentDashboard",
                mapper.constructType(String.class), Duration.ofMinutes(1), Duration.ofMinutes(2),
                1024 * 1024, 1024, true, false);
        RedisCacheStore emptyStore = new RedisCacheStore() {
            public byte[] get(String key) { return null; }
            public void put(String key, byte[] value, Duration ttl) { }
            public void evict(String key) { }
        };
        ErgTieredCache cache = new ErgTieredCache(descriptor, new CacheKeyFactory(mapper),
                new JacksonCacheValueCodec(mapper), emptyStore, () -> "tenant-a",
                new LocalCacheGenerationService(), new CacheFailurePolicy(3, Duration.ofSeconds(1)),
                new CacheLoadCoordinator());
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Future<String>> results = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                results.add(executor.submit(() -> cache.get("same-key", () -> {
                    loads.incrementAndGet();
                    started.countDown();
                    release.await();
                    return "value";
                })));
            }
            assertThat(started.await(2, java.util.concurrent.TimeUnit.SECONDS))
                    .as("one cache loader should be admitted for the shared miss")
                    .isTrue();
            release.countDown();
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo("value");
            }
        }

        assertThat(loads).hasValue(1);
    }

    @Test
    void rejectsASecondIndependentMissWhenLoadAdmissionIsFull() throws Exception {
        CacheLoadCoordinator coordinator = new CacheLoadCoordinator(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<String> first = executor.submit(() -> coordinator.load("key-a", () -> {
                entered.countDown();
                release.await();
                return "a";
            }));
            assertThat(entered.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> coordinator.load("key-b", () -> "b"))
                    .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);

            release.countDown();
            assertThat(first.get()).isEqualTo("a");
        }
    }
}
