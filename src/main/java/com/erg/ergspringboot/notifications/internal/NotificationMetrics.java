package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.notifications.persistence.NotificationDeviceRegistrationRepository;
import com.erg.ergspringboot.notifications.persistence.NotificationOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    static final String OTHER = "other";
    /** Tag values come from request/provider data, so every tag is mapped onto a bounded set. */
    static final Set<String> EVENT_TYPES = Set.of(
            "GENERAL_NOTICE", "SCHEDULE_PUBLISHED", "ASSIGNMENT_CREATED", "ASSIGNMENT_DUE_SOON",
            "ASSIGNMENT_OVERDUE", "ATTEMPT_SUBMITTED", "SUPPORT_NEEDED_LOW_SCORE");
    static final Set<String> PORTALS = Set.of("lms", "lcms", "elearning", "admin", "teacher", "student", "parent", "crm");
    static final Set<String> TARGET_KINDS = Set.of("registration_token", "fid", "topic", "unknown");
    static final Set<String> ERROR_CODES = Set.of(
            "UNKNOWN_TARGET_KIND", "TARGET_DECRYPT_FAILED", "SENDER_RUNTIME_ERROR", "DELIVERY_REQUEST_MISSING",
            "DELIVERY_ADAPTER_MISSING", "DELIVERY_ADAPTER_FAILED", "FID_TARGET_UNSUPPORTED", "PUSH_DISABLED",
            "FIREBASE_PROJECT_MISSING", "TARGET_MISSING", "ACCESS_TOKEN_MISSING", "PAYLOAD_TOO_LARGE", "IO_ERROR",
            "INTERRUPTED", "NOTIFICATION_MISSING", "UNSPECIFIED_ERROR", "INVALID_ARGUMENT", "UNREGISTERED",
            "SENDER_ID_MISMATCH", "QUOTA_EXCEEDED", "UNAVAILABLE", "INTERNAL", "THIRD_PARTY_AUTH_ERROR",
            "NOT_FOUND", "PERMISSION_DENIED", "UNAUTHENTICATED", "RESOURCE_EXHAUSTED");
    private static final Pattern HTTP_STATUS = Pattern.compile("HTTP_[1-5][0-9]{2}");

    private final Optional<MeterRegistry> meterRegistry;

    public NotificationMetrics(
            ObjectProvider<MeterRegistry> meterRegistry,
            NotificationOutboxRepository outboxRepository,
            NotificationDeviceRegistrationRepository deviceRepository
    ) {
        this.meterRegistry = Optional.ofNullable(meterRegistry.getIfAvailable());
        this.meterRegistry.ifPresent(registry -> {
            // Gauges are sampled on scrape threads without a tenant; count cluster-wide in maintenance scope
            // so strict RLS does not silently report zero.
            registry.gauge("notifications.outbox.pending", outboxRepository,
                    repository -> maintenance(() -> repository.countByStatus("pending")));
            registry.gauge("notifications.outbox.retry", outboxRepository,
                    repository -> maintenance(() -> repository.countByStatus("retry")));
            registry.gauge("notifications.outbox.dead", outboxRepository,
                    repository -> maintenance(() -> repository.countByStatus("dead")));
            registry.gauge(
                    "notifications.registration.active",
                    deviceRepository,
                    repository -> maintenance(repository::countByEnabledTrue)
            );
        });
    }

    public void created(String eventType, String portal, int recipients, int outbox) {
        meterRegistry.ifPresent(registry -> {
            Tags tags = Tags.of("eventType", eventTypeTag(eventType), "portal", portalTag(portal));
            registry.counter("notifications.created.count", tags).increment();
            registry.counter("notifications.recipients.count", tags).increment(recipients);
            registry.counter("notifications.outbox.created.count", tags).increment(outbox);
        });
    }

    public void sendResult(String targetKind, String errorCode, boolean success, long durationMs) {
        meterRegistry.ifPresent(registry -> {
            Tags tags = Tags.of(
                    "targetKind",
                    bounded(targetKind, TARGET_KINDS, false),
                    "result",
                    success ? "success" : "failure",
                    "errorCode",
                    errorCodeTag(errorCode)
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

    static String eventTypeTag(String eventType) {
        return bounded(eventType, EVENT_TYPES, true);
    }

    static String portalTag(String portal) {
        return bounded(portal, PORTALS, false);
    }

    static String errorCodeTag(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "none";
        }
        String normalized = errorCode.trim().toUpperCase(Locale.ROOT);
        return ERROR_CODES.contains(normalized) || HTTP_STATUS.matcher(normalized).matches() ? normalized : OTHER;
    }

    private static String bounded(String value, Set<String> allowed, boolean upperCase) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String trimmed = value.trim();
        String normalized = upperCase ? trimmed.toUpperCase(Locale.ROOT) : trimmed.toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : OTHER;
    }

    private static double maintenance(Supplier<Long> count) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            return count.get();
        }
    }
}
