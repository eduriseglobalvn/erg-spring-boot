package com.erg.ergspringboot.platform.cache;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class CacheFailurePolicy {

    private final int failureThreshold;
    private final long openNanos;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();

    public CacheFailurePolicy(int failureThreshold, Duration openDuration) {
        if (failureThreshold < 1) throw new IllegalArgumentException("failureThreshold must be positive");
        if (openDuration == null || openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openNanos = openDuration.toNanos();
    }

    public <T> T execute(Supplier<T> operation, T fallback) {
        if (System.nanoTime() < openUntil.get()) {
            return fallback;
        }
        try {
            T result = operation.get();
            consecutiveFailures.set(0);
            openUntil.set(0);
            return result;
        } catch (RuntimeException exception) {
            if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
                openUntil.set(System.nanoTime() + openNanos);
            }
            return fallback;
        }
    }

    public void execute(Runnable operation) {
        execute(() -> {
            operation.run();
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }

    public boolean isOpen() {
        return System.nanoTime() < openUntil.get();
    }
}

