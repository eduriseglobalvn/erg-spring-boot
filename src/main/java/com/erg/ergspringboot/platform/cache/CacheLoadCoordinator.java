package com.erg.ergspringboot.platform.cache;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class CacheLoadCoordinator {

    private static final int DEFAULT_MAX_IN_FLIGHT_KEYS = 1024;
    private final ConcurrentHashMap<Object, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    private final Semaphore admission;
    private final Duration maxWait;

    public CacheLoadCoordinator() {
        this(DEFAULT_MAX_IN_FLIGHT_KEYS);
    }

    public CacheLoadCoordinator(int maxInFlightKeys) {
        this(maxInFlightKeys, Duration.ofSeconds(5));
    }

    public CacheLoadCoordinator(int maxInFlightKeys, Duration maxWait) {
        this.maxWait = maxWait;
        if (maxInFlightKeys < 1) {
            throw new IllegalArgumentException("maxInFlightKeys must be positive");
        }
        this.admission = new Semaphore(maxInFlightKeys);
    }

    @SuppressWarnings("unchecked")
    public <T> T load(Object key, Callable<T> loader) throws Exception {
        CompletableFuture<Object> candidate = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(key, candidate);
        if (existing != null) {
            try {
                return (T) existing.get(maxWait.toMillis(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException exception) {
                throw rethrow(exception.getCause());
            } catch (TimeoutException timeout) {
                return loader.call(); // a hung leader must not block every waiter: load it ourselves
            }
        }
        if (!admission.tryAcquire()) {
            inFlight.remove(key, candidate);
            return loader.call(); // admission full: load uncoordinated instead of failing the request
        }
        try {
            T value = loader.call();
            candidate.complete(value);
            return value;
        } catch (Throwable throwable) {
            candidate.completeExceptionally(throwable);
            throw rethrow(throwable);
        } finally {
            inFlight.remove(key, candidate);
            admission.release();
        }
    }

    private Exception rethrow(Throwable throwable) throws Error {
        if (throwable instanceof Error error) throw error;
        if (throwable instanceof Exception exception) return exception;
        return new IllegalStateException(throwable);
    }
}
