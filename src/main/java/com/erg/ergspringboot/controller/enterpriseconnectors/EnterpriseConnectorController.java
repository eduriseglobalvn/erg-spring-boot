package com.erg.ergspringboot.controller.enterpriseconnectors;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorOperations;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.DeprovisionRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RegisterConnectorRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RetryRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RunRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.ConnectorResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.DeprovisionResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.SyncPreviewResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.SyncRunResponse;
import com.erg.ergspringboot.enterpriseconnectors.authorization.EnterpriseConnectorsRead;
import com.erg.ergspringboot.enterpriseconnectors.authorization.EnterpriseConnectorsSync;
import com.erg.ergspringboot.enterpriseconnectors.authorization.EnterpriseConnectorsWrite;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enterprise-connectors")
public class EnterpriseConnectorController {
    private final EnterpriseConnectorOperations operations;

    public EnterpriseConnectorController(EnterpriseConnectorOperations operations) {
        this.operations = operations;
    }

    @PostMapping
    @EnterpriseConnectorsWrite
    public ApiResponse<ConnectorResponse> register(
            @Valid @RequestBody RegisterConnectorRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Enterprise connector registered", operations.register(
                UserAccessContext.from(request), body, idempotencyKey, correlation(request)), request);
    }

    @GetMapping
    @EnterpriseConnectorsRead
    public ApiResponse<List<ConnectorResponse>> connectors(HttpServletRequest request) {
        return ok("Enterprise connectors loaded", operations.connectors(UserAccessContext.from(request)), request);
    }

    @GetMapping("/{connectorId}")
    @EnterpriseConnectorsRead
    public ApiResponse<ConnectorResponse> connectorById(@PathVariable String connectorId,
                                                        HttpServletRequest request) {
        return ok("Enterprise connector loaded", operations.connectorById(
                UserAccessContext.from(request), connectorId), request);
    }

    @PostMapping("/{connectorId}/dry-run")
    @EnterpriseConnectorsSync
    public ApiResponse<SyncPreviewResponse> dryRun(
            @PathVariable String connectorId,
            @Valid @RequestBody RunRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Enterprise connector dry-run completed", operations.dryRun(
                UserAccessContext.from(request), connectorId, body, idempotencyKey, correlation(request)), request);
    }

    @PostMapping("/{connectorId}/sync")
    @EnterpriseConnectorsSync
    public ApiResponse<SyncRunResponse> sync(
            @PathVariable String connectorId,
            @Valid @RequestBody RunRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Enterprise connector sync completed", operations.sync(
                UserAccessContext.from(request), connectorId, body, idempotencyKey, correlation(request)), request);
    }

    @PostMapping("/{connectorId}/runs/{runId}/retry")
    @EnterpriseConnectorsSync
    public ApiResponse<SyncRunResponse> retry(
            @PathVariable String connectorId,
            @PathVariable String runId,
            @Valid @RequestBody RetryRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Enterprise connector retry completed", operations.retry(
                UserAccessContext.from(request), connectorId, runId, body, idempotencyKey, correlation(request)), request);
    }

    @PostMapping("/{connectorId}/deprovision")
    @EnterpriseConnectorsSync
    public ApiResponse<DeprovisionResponse> deprovision(
            @PathVariable String connectorId,
            @Valid @RequestBody DeprovisionRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        return ok("Enterprise connector deprovision completed", operations.deprovision(
                UserAccessContext.from(request), connectorId, body, idempotencyKey, correlation(request)), request);
    }

    private String correlation(HttpServletRequest request) {
        return RequestCorrelation.current(request);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), correlation(request));
    }
}
