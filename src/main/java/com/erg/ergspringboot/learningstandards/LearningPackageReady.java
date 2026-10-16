package com.erg.ergspringboot.learningstandards;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

public record LearningPackageReady(String eventId, String tenantId, String packageId,
                                   String actorId, long aggregateVersion, OffsetDateTime occurredAt,
                                   int schemaVersion, String correlationId, String causationId) {
    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, packageId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
