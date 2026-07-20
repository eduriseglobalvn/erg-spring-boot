package com.erg.ergspringboot.service.notifications;

import com.erg.ergspringboot.notifications.config.NotificationPushProperties;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.repository.notifications.NotificationDeviceRegistrationRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeviceMaintenanceService {

    private final NotificationDeviceRegistrationRepository deviceRepository;
    private final NotificationPushProperties properties;
    private final NotificationMetrics metrics;

    public NotificationDeviceMaintenanceService(
            NotificationDeviceRegistrationRepository deviceRepository,
            NotificationPushProperties properties,
            NotificationMetrics metrics
    ) {
        this.deviceRepository = deviceRepository;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(cron = "${erg.notifications.push.stale-cleanup-cron:0 20 3 * * *}")
    public void scheduledCleanup() {
        if (properties.isEnabled()) {
            disableStaleDevices();
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
