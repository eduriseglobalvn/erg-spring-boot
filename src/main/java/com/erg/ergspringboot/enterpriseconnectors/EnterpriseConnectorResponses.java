package com.erg.ergspringboot.enterpriseconnectors;

import java.time.OffsetDateTime;
import java.util.List;

/** Stable scalar responses; provider payloads and persistence entities stay internal. */
public final class EnterpriseConnectorResponses {
    private EnterpriseConnectorResponses() {
    }

    public record ConnectorResponse(
            String id,
            String tenantId,
            String providerKind,
            String adapterKey,
            String providerKey,
            String displayName,
            String endpoint,
            String secretRef,
            String mappingVersion,
            String mappingHash,
            String status,
            long recordVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record SyncPreviewResponse(
            String connectorId,
            String runId,
            String mode,
            int additions,
            int updates,
            int removals,
            int total,
            List<String> sampleExternalIds,
            String status,
            long recordVersion,
            OffsetDateTime createdAt) {
    }

    public record SyncRunResponse(
            String connectorId,
            String runId,
            String mode,
            int processed,
            int created,
            int updated,
            int failed,
            int retryCount,
            String status,
            String errorCode,
            long recordVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record DeprovisionResponse(
            String connectorId,
            String runId,
            int requested,
            int deprovisioned,
            int skipped,
            String status,
            long recordVersion,
            OffsetDateTime createdAt) {
    }
}
