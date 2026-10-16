package com.erg.ergspringboot.learningoutcomes;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

public record LearningPathCreated(
        String eventId,
        String tenantId,
        String pathId,
        String actorId,
        long aggregateVersion,
        OffsetDateTime occurredAt,
        int schemaVersion,
        String correlationId,
        String causationId) {
    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, pathId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
