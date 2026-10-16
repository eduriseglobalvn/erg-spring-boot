package com.erg.ergspringboot.learningstandards;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

public record XapiStatementAccepted(String eventId, String tenantId, String statementId,
                                    String actorId, long aggregateVersion, OffsetDateTime occurredAt,
                                    int schemaVersion, String correlationId, String causationId) {
    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, statementId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
