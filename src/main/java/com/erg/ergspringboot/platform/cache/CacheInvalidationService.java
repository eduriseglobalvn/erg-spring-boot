package com.erg.ergspringboot.platform.cache;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class CacheInvalidationService {

    private static final Object RESOURCE_KEY = CacheInvalidationService.class.getName() + ".requests";

    private final CacheGenerationService generations;
    private final CacheInvalidationPublisher publisher;
    private final String sourceInstanceId;

    public CacheInvalidationService(
            CacheGenerationService generations,
            CacheInvalidationPublisher publisher
    ) {
        this(generations, publisher, UUID.randomUUID().toString());
    }

    CacheInvalidationService(
            CacheGenerationService generations,
            CacheInvalidationPublisher publisher,
            String sourceInstanceId
    ) {
        this.generations = generations;
        this.publisher = publisher;
        this.sourceInstanceId = sourceInstanceId;
    }

    public void clearTenantCache(String tenantId, String cacheName) {
        Request request = new Request(require(tenantId, "tenantId"), require(cacheName, "cacheName"));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            apply(request);
            return;
        }
        requests().add(request);
    }

    @SuppressWarnings("unchecked")
    private Set<Request> requests() {
        Set<Request> existing = (Set<Request>) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (existing != null) return existing;
        Set<Request> created = new LinkedHashSet<>();
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, created);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                created.forEach(CacheInvalidationService.this::apply);
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(RESOURCE_KEY)) {
                    TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
                }
            }
        });
        return created;
    }

    private void apply(Request request) {
        long generation = generations.increment(request.tenantId(), request.cacheName());
        if (generation >= 0) {
            publisher.publish(CacheInvalidationMessage.generation(sourceInstanceId,
                    request.tenantId(), request.cacheName(), generation));
        }
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private record Request(String tenantId, String cacheName) {
    }
}

