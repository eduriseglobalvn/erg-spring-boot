package com.erg.ergspringboot.platform.redis;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class RedisMetricsBinder implements MeterBinder {
    private final String role;
    private final RedisConnectionFactory connectionFactory;
    private final AtomicLong usedMemory = new AtomicLong();
    private final AtomicLong maxMemory = new AtomicLong();
    private final AtomicLong evictedKeys = new AtomicLong();
    private final AtomicLong rejectedConnections = new AtomicLong();
    private final AtomicLong aofHealthy = new AtomicLong(1);
    private final AtomicLong scrapeHealthy = new AtomicLong();

    public RedisMetricsBinder(String role, RedisConnectionFactory connectionFactory) {
        this.role = role;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "erg.redis.memory.used", "bytes", usedMemory);
        gauge(registry, "erg.redis.memory.max", "bytes", maxMemory);
        gauge(registry, "erg.redis.evicted.keys", "keys", evictedKeys);
        gauge(registry, "erg.redis.rejected.connections", "connections", rejectedConnections);
        gauge(registry, "erg.redis.aof.healthy", "status", aofHealthy);
        gauge(registry, "erg.redis.info.scrape.healthy", "status", scrapeHealthy);
        Gauge.builder("erg.redis.memory.ratio", this, binder -> binder.memoryRatio())
                .tag("role", role).register(registry);
    }

    @Scheduled(fixedDelayString = "${erg.redis.metrics.refresh-interval:15s}")
    public void refresh() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            Properties info = connection.serverCommands().info();
            usedMemory.set(number(info, "used_memory"));
            maxMemory.set(number(info, "maxmemory"));
            evictedKeys.set(number(info, "evicted_keys"));
            rejectedConnections.set(number(info, "rejected_connections"));
            aofHealthy.set("ok".equalsIgnoreCase(info.getProperty("aof_last_write_status", "ok")) ? 1 : 0);
            scrapeHealthy.set(1);
        } catch (RuntimeException failure) {
            scrapeHealthy.set(0);
        }
    }

    private void gauge(MeterRegistry registry, String name, String unit, AtomicLong value) {
        Gauge.builder(name, value, AtomicLong::doubleValue).baseUnit(unit).tag("role", role).register(registry);
    }

    private long number(Properties info, String key) {
        try {
            return Long.parseLong(info.getProperty(key, "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double memoryRatio() {
        long max = maxMemory.get();
        return max <= 0 ? 0 : (double) usedMemory.get() / max;
    }
}
