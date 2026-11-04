package com.erg.ergspringboot.enterpriseconnectors.internal;

import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.ConnectorResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.DeprovisionResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.SyncPreviewResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.SyncRunResponse;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnector;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRun;
import java.util.List;

/** Maps connector and run entities to the published response DTOs; secrets are always masked. */
final class EnterpriseConnectorResponseMapper {

    private EnterpriseConnectorResponseMapper() {
    }

    static long version(EnterpriseConnector value) {
        return value.getRecordVersion() == null ? 0 : value.getRecordVersion();
    }

    static long version(EnterpriseConnectorRun value) {
        return value.getRecordVersion() == null ? 0 : value.getRecordVersion();
    }

    static ConnectorResponse connectorResponse(EnterpriseConnector value) {
        return new ConnectorResponse(value.getId(), value.getTenantId(), value.getProviderKind(), value.getAdapterKey(),
                value.getProviderKey(), value.getDisplayName(), value.getEndpoint(), "secret://***",
                value.getMappingVersion(), value.getMappingHash(), value.getStatus(), version(value),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    static SyncPreviewResponse previewResponse(EnterpriseConnectorRun run) {
        List<String> samples = splitSample(run.getSampleExternalIds());
        return new SyncPreviewResponse(run.getConnectorId(), run.getId(), run.getMode(), run.getCreatedCount(),
                run.getUpdatedCount(), Math.max(0, run.getProcessed() - run.getCreatedCount() - run.getUpdatedCount()),
                run.getProcessed(), samples, run.getStatus(), version(run), run.getCreatedAt());
    }

    static SyncRunResponse syncResponse(EnterpriseConnectorRun run) {
        return new SyncRunResponse(run.getConnectorId(), run.getId(), run.getMode(), run.getProcessed(),
                run.getCreatedCount(), run.getUpdatedCount(), run.getFailedCount(), run.getRetryCount(),
                run.getStatus(), run.getErrorCode(), version(run), run.getCreatedAt(), run.getUpdatedAt());
    }

    static DeprovisionResponse deprovisionResponse(EnterpriseConnectorRun run) {
        return new DeprovisionResponse(run.getConnectorId(), run.getId(), run.getProcessed(), run.getCreatedCount(),
                run.getFailedCount(), run.getStatus(), version(run), run.getCreatedAt());
    }

    private static List<String> splitSample(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(",", EnterpriseConnectorInputs.MAX_SAMPLE_IDS));
    }
}
