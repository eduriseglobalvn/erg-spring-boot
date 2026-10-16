package com.erg.ergspringboot.learningoutcomes;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

/** Versioned progress event; evidence text is deliberately excluded from the payload. */
public record CompetencyProgressRecorded(
        String eventId,
        String tenantId,
        String attainmentId,
        String learnerId,
        String competencyId,
        String learningPathId,
        int progressPercent,
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
