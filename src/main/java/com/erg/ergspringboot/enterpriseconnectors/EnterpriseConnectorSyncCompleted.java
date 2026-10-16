package com.erg.ergspringboot.enterpriseconnectors;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

public record EnterpriseConnectorSyncCompleted(String eventId, String tenantId, String connectorId,
                                               String runId, String actorId, long aggregateVersion,
                                               String mode, int processed, int created, int updated, int failed,
                                               OffsetDateTime occurredAt, int schemaVersion, String correlationId,
                                               String causationId) implements EnterpriseConnectorEvent {
    @Override
    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, runId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
