package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.repository.notifications.NotificationDeviceRegistrationRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeviceMaintenanceService {

    private final NotificationDeviceRegistrationRepository deviceRepository;
    private final NotificationPushProperties properties;
    private final NotificationMetrics metrics;
    private final ClusterJobLock jobLock;

    public NotificationDeviceMaintenanceService(
            NotificationDeviceRegistrationRepository deviceRepository,
            NotificationPushProperties properties,
            NotificationMetrics metrics
    ) {
        this(deviceRepository, properties, metrics, (ClusterJobLock) null);
    }

    @Autowired
    public NotificationDeviceMaintenanceService(
            NotificationDeviceRegistrationRepository deviceRepository,
            NotificationPushProperties properties,
            NotificationMetrics metrics,
            ObjectProvider<ClusterJobLock> jobLock
    ) {
        this(deviceRepository, properties, metrics, jobLock.getIfAvailable());
    }

    NotificationDeviceMaintenanceService(
            NotificationDeviceRegistrationRepository deviceRepository,
            NotificationPushProperties properties,
            NotificationMetrics metrics,
            ClusterJobLock jobLock
    ) {
        this.deviceRepository = deviceRepository;
        this.properties = properties;
        this.metrics = metrics;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${erg.notifications.push.stale-cleanup-cron:0 20 3 * * *}")
    public void scheduledCleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        // Maintenance scope is installed before any transaction starts so the RLS GUC is applied.
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            if (jobLock == null) {
                disableStaleDevices();
            } else {
                jobLock.runExclusively("notification-device-stale-cleanup", this::disableStaleDevices);
            }
        }
    }

    @Transactional
    public int disableStaleDevices() {
        TenantContext previous = TenantContext.current();
        if (previous == null) TenantContext.set(TenantContext.maintenance());
        try {
        Duration age = properties.getStaleRegistrationAge();
        Duration effectiveAge = age == null || age.isNegative() || age.isZero() ? Duration.ofDays(30) : age;
        OffsetDateTime now = OffsetDateTime.now();
        int disabled = deviceRepository.disableStaleDevices(now.minus(effectiveAge), now);
        metrics.staleDisabled(disabled);
        return disabled;
        } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.set(previous);
        }
    }
}
