package com.erg.ergspringboot.enterpriseconnectors;

import java.util.List;

/** Provider-neutral adapter port. No SDK DTO, HTTP client or secret crosses it. */
public interface EnterpriseConnectorProviderPort {
    String adapterKey();

    boolean supports(EnterpriseConnectorRequests.ProviderKind providerKind);

    PreviewResult preview(ConnectorDescriptor connector, String cursor);

    SyncResult sync(ConnectorDescriptor connector, String cursor);

    DeprovisionResult deprovision(ConnectorDescriptor connector, List<String> externalIds);

    record ConnectorDescriptor(
            String providerKey,
            EnterpriseConnectorRequests.ProviderKind providerKind,
            String mappingVersion,
            String mappingHash) {
    }

    record PreviewResult(int additions, int updates, int removals, List<String> sampleExternalIds) {
    }

    record SyncResult(int processed, int created, int updated, int failed,
                      int retryCount, String status, String errorCode) {
    }

    record DeprovisionResult(int deprovisioned, int skipped, String status) {
    }
}
