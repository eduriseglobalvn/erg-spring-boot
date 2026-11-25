package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.calendar.CalendarPublishedBatchProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import com.erg.ergspringboot.platform.security.TenantContext;
import org.springframework.transaction.annotation.Propagation;

/** Notifications-owned consumer for the public CalendarBatchPublished event: one notification per teacher. */
@Component
public class CalendarBatchPublishedConsumer {

    private static final Logger log = LoggerFactory.getLogger(CalendarBatchPublishedConsumer.class);

    private final CalendarPublishedBatchProjection calendar;
    private final NotificationCommandService notifications;

    public CalendarBatchPublishedConsumer(
            CalendarPublishedBatchProjection calendar,
            NotificationCommandService notifications
    ) {
        this.calendar = calendar;
        this.notifications = notifications;
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    public void on(CalendarBatchPublished event) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(event.tenantId(), null, "system"))) {
            var batch = calendar.findPublishedBatch(event.tenantId(), event.batchId()).orElse(null);
            // A batch cancelled (or deleted) between publish and delivery is not an error: nobody should hear about it.
            if (batch == null || !"PUBLISHED".equals(batch.status()) || batch.slots().isEmpty()) {
                log.info("Skip schedule-published notification {}: batch missing, not published or empty", event.eventId());
                return;
            }
            notifications.enqueueAll(ScheduleNotificationComposer.compose(ScheduleNotificationComposer.Kind.PUBLISHED, event.tenantId(),
                            event.actorId(), event.eventId(), event.aggregateVersion(), "SCHEDULE_PUBLISHED", batch));
        }
    }
}
