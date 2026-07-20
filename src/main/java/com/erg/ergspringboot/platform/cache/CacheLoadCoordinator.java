package com.erg.ergspringboot.platform.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public final class CacheLoadCoordinator {

    private final ConcurrentHashMap<Object, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

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
        try {
            T value = loader.call();
            candidate.complete(value);
            return value;
        } catch (Throwable throwable) {
            candidate.completeExceptionally(throwable);
            throw rethrow(throwable);
        } finally {
            inFlight.remove(key, candidate);
        }
    }

    private Exception rethrow(Throwable throwable) throws Error {
        if (throwable instanceof Error error) throw error;
        if (throwable instanceof Exception exception) return exception;
        return new IllegalStateException(throwable);
    }
}

