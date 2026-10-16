package com.erg.ergspringboot.learningoutcomes;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

public record OutcomeCertificateIssued(
        String eventId,
        String tenantId,
        String certificateId,
        String learnerId,
        String learningPathId,
        String actorId,
        long aggregateVersion,
        OffsetDateTime occurredAt,
        int schemaVersion,
        String correlationId,
        String causationId) {
    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, certificateId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
