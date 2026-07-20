package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.repository.notifications.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.repository.notifications.NotificationOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final Optional<MeterRegistry> meterRegistry;

    public NotificationMetrics(
            ObjectProvider<MeterRegistry> meterRegistry,
            NotificationOutboxRepository outboxRepository,
            NotificationDeviceRegistrationRepository deviceRepository
    ) {
        this.meterRegistry = Optional.ofNullable(meterRegistry.getIfAvailable());
        this.meterRegistry.ifPresent(registry -> {
            registry.gauge("notifications.outbox.pending", outboxRepository, repository -> repository.countByStatus("pending"));
            registry.gauge("notifications.outbox.retry", outboxRepository, repository -> repository.countByStatus("retry"));
            registry.gauge("notifications.outbox.dead", outboxRepository, repository -> repository.countByStatus("dead"));
            registry.gauge(
                    "notifications.registration.active",
                    deviceRepository,
                    NotificationDeviceRegistrationRepository::countByEnabledTrue
            );
        });
    }

    public void created(String eventType, String portal, int recipients, int outbox) {
        meterRegistry.ifPresent(registry -> {
            Tags tags = Tags.of("eventType", value(eventType), "portal", value(portal));
            registry.counter("notifications.created.count", tags).increment();
            registry.counter("notifications.recipients.count", tags).increment(recipients);
            registry.counter("notifications.outbox.created.count", tags).increment(outbox);
        });
    }

    public void sendResult(String targetKind, String errorCode, boolean success, long durationMs) {
        meterRegistry.ifPresent(registry -> {
            Tags tags = Tags.of(
                    "targetKind",
                    value(targetKind),
                    "result",
                    success ? "success" : "failure",
                    "errorCode",
                    value(errorCode)
            );
            registry.counter("notifications.send.count", tags).increment();
            registry.timer("notifications.send.latency", tags).record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
        });
    }

    public void staleDisabled(int count) {
        meterRegistry.ifPresent(registry -> registry
                .counter("notifications.registration.stale.disabled.count")
                .increment(Math.max(0, count)));
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "none" : value.trim();
    }
}
