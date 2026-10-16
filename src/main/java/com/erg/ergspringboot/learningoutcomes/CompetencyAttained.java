package com.erg.ergspringboot.learningoutcomes;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

public record CompetencyAttained(
        String eventId,
        String tenantId,
        String attainmentId,
        String learnerId,
        String competencyId,
        String learningPathId,
        String actorId,
        long aggregateVersion,
        OffsetDateTime occurredAt,
        int schemaVersion,
        String correlationId,
        String causationId) {
    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, attainmentId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
