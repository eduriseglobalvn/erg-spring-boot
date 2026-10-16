package com.erg.ergspringboot.notifications.delivery;

import com.erg.ergspringboot.notifications.push.SendResult;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fail-closed registry for durable notification delivery. A key has exactly one adapter and every
 * persisted key must be registered at startup, so adding a persisted kind without an implementation
 * fails before the worker can silently drop messages.
 */
@Component
public class NotificationDeliveryAdapterRegistry {

    private final Map<NotificationDeliveryKey, NotificationDeliveryAdapter> adapters;

    @Autowired
    public NotificationDeliveryAdapterRegistry(Collection<? extends NotificationDeliveryAdapter> adapters) {
        this(adapters, NotificationDeliveryKinds.persistedKeys());
    }

    public NotificationDeliveryAdapterRegistry(
            Collection<? extends NotificationDeliveryAdapter> adapters,
            Collection<NotificationDeliveryKey> requiredKeys
    ) {
        Map<NotificationDeliveryKey, NotificationDeliveryAdapter> index = new LinkedHashMap<>();
        for (NotificationDeliveryAdapter adapter : adapters) {
            if (adapter == null || adapter.keys() == null || adapter.keys().isEmpty()) {
                throw new IllegalStateException("Notification delivery adapter must declare at least one key.");
            }
            for (NotificationDeliveryKey key : adapter.keys()) {
                if (key == null) {
                    throw new IllegalStateException("Notification delivery adapter declared a null key.");
                }
                NotificationDeliveryAdapter previous = index.putIfAbsent(key, adapter);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate notification delivery adapter for " + key);
                }
            }
        }
        for (NotificationDeliveryKey required : requiredKeys) {
            if (!index.containsKey(required)) {
                throw new IllegalStateException("No notification delivery adapter registered for " + required);
            }
        }
        this.adapters = Map.copyOf(index);
    }

    public SendResult deliver(NotificationDeliveryRequest request) {
        if (request == null) {
            return SendResult.failure(false, false, 0, "DELIVERY_REQUEST_MISSING",
                    "Notification delivery request is missing.", null, 0);
        }
        NotificationDeliveryAdapter adapter = adapters.get(request.key());
        if (adapter == null) {
            return SendResult.failure(false, false, 0, "DELIVERY_ADAPTER_MISSING",
                    "No notification delivery adapter is registered for " + request.key(), null, 0);
        }
        try {
            return adapter.deliver(request);
        } catch (RuntimeException exception) {
            return SendResult.failure(true, false, 0, "DELIVERY_ADAPTER_FAILED",
                    exception.getMessage(), null, 0);
        }
    }

    public Set<NotificationDeliveryKey> keys() {
        return adapters.keySet();
    }
}
