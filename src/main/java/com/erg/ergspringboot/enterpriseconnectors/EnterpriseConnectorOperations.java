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
import org.springframework.stereotype.Service;

/** Public facade for SIS, HRIS and SCIM connector use cases. */
@Service
public class EnterpriseConnectorOperations {
    private final EnterpriseConnectorPort port;

    public EnterpriseConnectorOperations(EnterpriseConnectorPort port) {
        this.port = port;
    }

    public ConnectorResponse register(UserAccessContext access, RegisterConnectorRequest request,
                                      String idempotencyKey, String correlationId) {
        return port.register(access, request, idempotencyKey, correlationId);
    }

    public List<ConnectorResponse> connectors(UserAccessContext access) {
        return port.connectors(access);
    }

    public ConnectorResponse connectorById(UserAccessContext access, String connectorId) {
        return port.connectorById(access, connectorId);
    }

    public SyncPreviewResponse dryRun(UserAccessContext access, String connectorId, RunRequest request,
                                      String idempotencyKey, String correlationId) {
        return port.dryRun(access, connectorId, request, idempotencyKey, correlationId);
    }

    public SyncRunResponse sync(UserAccessContext access, String connectorId, RunRequest request,
                                String idempotencyKey, String correlationId) {
        return port.sync(access, connectorId, request, idempotencyKey, correlationId);
    }

    public SyncRunResponse retry(UserAccessContext access, String connectorId, String runId, RetryRequest request,
                                 String idempotencyKey, String correlationId) {
        return port.retry(access, connectorId, runId, request, idempotencyKey, correlationId);
    }

    public DeprovisionResponse deprovision(UserAccessContext access, String connectorId,
                                           DeprovisionRequest request, String idempotencyKey,
                                           String correlationId) {
        return port.deprovision(access, connectorId, request, idempotencyKey, correlationId);
    }
}
