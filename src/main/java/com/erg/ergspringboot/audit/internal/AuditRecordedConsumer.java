package com.erg.ergspringboot.audit.internal;

import com.erg.ergspringboot.audit.AuditRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Durable post-commit hook for future audit projections and external export sinks. */
@Component
public class AuditRecordedConsumer {
    private static final Logger log = LoggerFactory.getLogger(AuditRecordedConsumer.class);

    @ApplicationModuleListener
    public void on(AuditRecorded event) {
        // Keep the listener deliberately side-effect free; the append is already durable.
        // Modulith persists the publication so a future sink can replay it without losing context.
        log.debug("Audit event published eventId={} tenantId={} action={}",
                event.eventId(), event.tenantId(), event.action());
    }
}
