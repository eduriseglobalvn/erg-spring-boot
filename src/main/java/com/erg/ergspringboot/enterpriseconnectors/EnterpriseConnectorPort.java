package com.erg.ergspringboot.enterpriseconnectors;

import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.DeprovisionRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RegisterConnectorRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RetryRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RunRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.ConnectorResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.DeprovisionResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.SyncPreviewResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.SyncRunResponse;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;

/** Public use-case port. Infrastructure adapters implement this boundary. */
public interface EnterpriseConnectorPort {
    ConnectorResponse register(UserAccessContext access, RegisterConnectorRequest request,
                               String idempotencyKey, String correlationId);

    List<ConnectorResponse> connectors(UserAccessContext access);

    ConnectorResponse connectorById(UserAccessContext access, String connectorId);

    SyncPreviewResponse dryRun(UserAccessContext access, String connectorId, RunRequest request,
                               String idempotencyKey, String correlationId);

    SyncRunResponse sync(UserAccessContext access, String connectorId, RunRequest request,
                         String idempotencyKey, String correlationId);

    SyncRunResponse retry(UserAccessContext access, String connectorId, String runId, RetryRequest request,
                          String idempotencyKey, String correlationId);

    DeprovisionResponse deprovision(UserAccessContext access, String connectorId, DeprovisionRequest request,
                                    String idempotencyKey, String correlationId);
}
