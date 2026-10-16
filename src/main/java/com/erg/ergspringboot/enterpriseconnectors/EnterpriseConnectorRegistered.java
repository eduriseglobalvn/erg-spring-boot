package com.erg.ergspringboot.enterpriseconnectors;

import com.erg.ergspringboot.platform.events.ModuleEventMetadata;
import java.time.OffsetDateTime;

public record EnterpriseConnectorRegistered(String eventId, String tenantId, String connectorId,
                                            String actorId, long aggregateVersion, OffsetDateTime occurredAt,
                                            int schemaVersion, String correlationId, String causationId)
        implements EnterpriseConnectorEvent {
    @Override
    public ModuleEventMetadata metadata() {
        return new ModuleEventMetadata(eventId, schemaVersion, tenantId, actorId, connectorId,
                aggregateVersion, occurredAt, correlationId, causationId);
    }
}
