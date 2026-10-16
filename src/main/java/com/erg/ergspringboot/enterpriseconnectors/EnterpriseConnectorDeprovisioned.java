package com.erg.ergspringboot.enterpriseconnectors;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

public record EnterpriseConnectorDeprovisioned(String eventId, String tenantId, String connectorId,
                                               String runId, String actorId, long aggregateVersion,
                                               int requested, int deprovisioned, int skipped,
                                               OffsetDateTime occurredAt, int schemaVersion, String correlationId,
                                               String causationId) implements EnterpriseConnectorEvent {
    @Override
    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, runId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
