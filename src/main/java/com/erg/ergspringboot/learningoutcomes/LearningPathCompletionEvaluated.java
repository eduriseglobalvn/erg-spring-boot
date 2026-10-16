package com.erg.ergspringboot.learningoutcomes;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

public record LearningPathCompletionEvaluated(
        String eventId,
        String tenantId,
        String completionId,
        String learnerId,
        String learningPathId,
        String status,
        int progressPercent,
        String actorId,
        long aggregateVersion,
        OffsetDateTime occurredAt,
        int schemaVersion,
        String correlationId,
        String causationId) {
    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, completionId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
