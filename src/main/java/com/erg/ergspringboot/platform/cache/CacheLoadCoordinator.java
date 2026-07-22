package com.erg.ergspringboot.platform.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

public final class CacheLoadCoordinator {

    private static final int DEFAULT_MAX_IN_FLIGHT_KEYS = 1024;
    private final ConcurrentHashMap<Object, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    private final Semaphore admission;

    public CacheLoadCoordinator() {
        this(DEFAULT_MAX_IN_FLIGHT_KEYS);
    }

    public CacheLoadCoordinator(int maxInFlightKeys) {
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
                return (T) existing.get();
            } catch (ExecutionException exception) {
                throw rethrow(exception.getCause());
            }
        }
        if (!admission.tryAcquire()) {
            inFlight.remove(key, candidate);
            throw new RejectedExecutionException("Cache load admission is full");
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
