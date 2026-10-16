package com.erg.ergspringboot.learningstandards;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

public record LtiLaunchAccepted(String eventId, String tenantId, String registrationId,
                                String subject, String actorId, long aggregateVersion,
                                OffsetDateTime occurredAt, int schemaVersion,
                                String correlationId, String causationId) {
    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, registrationId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
