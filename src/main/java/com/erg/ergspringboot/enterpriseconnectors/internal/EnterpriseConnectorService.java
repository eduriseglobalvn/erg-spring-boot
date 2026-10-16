package com.erg.ergspringboot.enterpriseconnectors.internal;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorDeprovisioned;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorEvent;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorOperations;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorPolicy;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorPort;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorProviderPort;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRegistered;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.DeprovisionRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.ProviderKind;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RegisterConnectorRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RetryRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorRequests.RunRequest;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.ConnectorResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.DeprovisionResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.SyncPreviewResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorResponses.SyncRunResponse;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorSyncCompleted;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.enterpriseconnectors.EnterpriseConnector;
import com.erg.ergspringboot.model.enterpriseconnectors.EnterpriseConnectorCommandReceipt;
import com.erg.ergspringboot.model.enterpriseconnectors.EnterpriseConnectorRun;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.enterpriseconnectors.EnterpriseConnectorCommandReceiptRepository;
import com.erg.ergspringboot.repository.enterpriseconnectors.EnterpriseConnectorRepository;
import com.erg.ergspringboot.repository.enterpriseconnectors.EnterpriseConnectorRunRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable connector orchestration. Provider adapters return bounded scalar
 * results; no upstream payload, credential or SDK type enters this service.
 */
@Service
public class EnterpriseConnectorService implements EnterpriseConnectorPort {
    private static final String ACTIVE = "ACTIVE";
    private static final String DRY_RUN = "DRY_RUN";
    private static final String SYNC = "SYNC";
    private static final String RETRY = "RETRY";
    private static final String DEPROVISION = "DEPROVISION";
    private static final String PREVIEW = "PREVIEW";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final int MAX_RETRIES = 3;
    private static final int MAX_SAMPLE_IDS = 20;

    private final EnterpriseConnectorRepository connectors;
    private final EnterpriseConnectorRunRepository runs;
    private final EnterpriseConnectorCommandReceiptRepository receipts;
    private final EnterpriseConnectorProviderRegistry registry;
    private final ApplicationEventPublisher events;
    private final AuditOperations audit;

    @Autowired
    public EnterpriseConnectorService(
            EnterpriseConnectorRepository connectors,
            EnterpriseConnectorRunRepository runs,
            EnterpriseConnectorCommandReceiptRepository receipts,
            EnterpriseConnectorProviderRegistry registry,
            ApplicationEventPublisher events,
            AuditOperations audit) {
        this.connectors = connectors;
        this.runs = runs;
        this.receipts = receipts;
        this.registry = registry;
        this.events = events;
        this.audit = audit;
    }

    @Override
    @Transactional("postgresTransactionManager")
    public ConnectorResponse register(UserAccessContext access, RegisterConnectorRequest request,
                                      String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureWrite();
        RegisterConnectorRequest safe = Objects.requireNonNull(request, "request");
        ProviderKind providerKind = Objects.requireNonNull(safe.providerKind(), "providerKind");
        String adapterKey = required(safe.adapterKey(), "adapterKey", 64).toUpperCase(Locale.ROOT);
        String providerKey = required(safe.providerKey(), "providerKey", 96);
        String displayName = required(safe.displayName(), "displayName", 255);
        String secretRef = secret(safe.secretRef());
        String endpoint = optional(safe.endpoint(), 255);
        EnterpriseConnectorPolicy.Mapping mapping = mapping(safe.mappingVersion(), safe.fieldMapping());
        EnterpriseConnectorProviderPort provider = registry.require(adapterKey);
        if (!provider.supports(providerKind)) throw new BadRequestException("adapter does not support provider kind");
        String key = key(idempotencyKey);
        String hash = EnterpriseConnectorPolicy.sha256(String.join("\u001f", providerKind.name(), adapterKey,
                providerKey, displayName, endpoint == null ? "" : endpoint, mapping.version(), mapping.hash()));
        EnterpriseConnectorCommandReceipt replay = begin(tenant, "REGISTER_CONNECTOR", key, hash);
        if (replay != null) return connectorResponse(findConnector(tenant, replay.getAggregateId()));
        if (connectors.findByTenantIdAndProviderKey(tenant, providerKey).isPresent()) {
            throw new ConflictException("providerKey is already registered");
        }
        EnterpriseConnector value = new EnterpriseConnector();
        value.setTenantId(tenant);
        value.setProviderKind(providerKind.name());
        value.setAdapterKey(adapterKey);
        value.setProviderKey(providerKey);
        value.setDisplayName(displayName);
        value.setEndpoint(endpoint);
        value.setSecretRef(secretRef);
        value.setMappingVersion(mapping.version());
        value.setMappingHash(mapping.hash());
        value.setStatus(ACTIVE);
        value.setCreatedBy(access.userId());
        value.setUpdatedBy(access.userId());
        EnterpriseConnector saved = connectors.saveAndFlush(value);
        saveReceipt(tenant, "REGISTER_CONNECTOR", key, hash, "ENTERPRISE_CONNECTOR", saved.getId());
        String eventId = IdGenerator.newId();
        publish(new EnterpriseConnectorRegistered(eventId, tenant, saved.getId(), access.userId(), version(saved),
                now(), 1, correlation(correlationId, eventId), correlation(correlationId, eventId)));
        audit(access, "ENTERPRISE_CONNECTOR_REGISTERED", "ENTERPRISE_CONNECTOR", saved.getId(), key,
                Map.of("providerKind", providerKind.name(), "adapterKey", adapterKey, "mappingHash", mapping.hash()));
        return connectorResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<ConnectorResponse> connectors(UserAccessContext access) {
        String tenant = tenant(access);
        ensureRead();
        return connectors.findTop100ByTenantIdOrderByUpdatedAtDesc(tenant).stream().map(this::connectorResponse).toList();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public ConnectorResponse connectorById(UserAccessContext access, String connectorId) {
        String tenant = tenant(access);
        ensureRead();
        return connectorResponse(findConnector(tenant, required(connectorId, "connectorId", 96)));
    }

    @Override
    @Transactional("postgresTransactionManager")
    public SyncPreviewResponse dryRun(UserAccessContext access, String connectorId, RunRequest request,
                                      String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureSync();
        RunRequest safe = Objects.requireNonNull(request, "request");
        EnterpriseConnector connector = findConnector(tenant, required(connectorId, "connectorId", 96));
        assertVersion(connector, safe.expectedVersion());
        EnterpriseConnectorProviderPort provider = provider(connector);
        String key = key(idempotencyKey);
        String hash = EnterpriseConnectorPolicy.sha256("DRY_RUN\u001f" + connector.getId() + "\u001f"
                + safe.expectedVersion() + "\u001f" + optional(safe.cursor(), 96));
        EnterpriseConnectorCommandReceipt replay = begin(tenant, DRY_RUN, key, hash);
        if (replay != null) return previewResponse(findRun(tenant, replay.getAggregateId()));
        EnterpriseConnectorProviderPort.PreviewResult result = provider.preview(descriptor(connector), safe.cursor());
        EnterpriseConnectorRun run = newRun(connector, DRY_RUN, PREVIEW, access.userId());
        run.setProcessed(result.additions() + result.updates() + result.removals());
        run.setCreatedCount(result.additions());
        run.setUpdatedCount(result.updates());
        run.setFailedCount(0);
        run.setSampleExternalIds(joinSample(result.sampleExternalIds()));
        EnterpriseConnectorRun saved = runs.saveAndFlush(run);
        saveReceipt(tenant, DRY_RUN, key, hash, "ENTERPRISE_CONNECTOR_RUN", saved.getId());
        audit(access, "ENTERPRISE_CONNECTOR_DRY_RUN", "ENTERPRISE_CONNECTOR_RUN", saved.getId(), key,
                Map.of("connectorId", connector.getId(), "total", run.getProcessed()));
        return previewResponse(saved);
    }

    @Override
    @Transactional("postgresTransactionManager")
    public SyncRunResponse sync(UserAccessContext access, String connectorId, RunRequest request,
                                String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureSync();
        RunRequest safe = Objects.requireNonNull(request, "request");
        EnterpriseConnector connector = findConnector(tenant, required(connectorId, "connectorId", 96));
        assertVersion(connector, safe.expectedVersion());
        String key = key(idempotencyKey);
        String hash = EnterpriseConnectorPolicy.sha256("SYNC\u001f" + connector.getId() + "\u001f"
                + safe.expectedVersion() + "\u001f" + optional(safe.cursor(), 96));
        EnterpriseConnectorCommandReceipt replay = begin(tenant, SYNC, key, hash);
        if (replay != null) return syncResponse(findRun(tenant, replay.getAggregateId()));
        EnterpriseConnectorProviderPort.SyncResult result = provider(connector).sync(descriptor(connector), safe.cursor());
        EnterpriseConnectorRun run = newRun(connector, SYNC, result.status(), access.userId());
        apply(run, result);
        EnterpriseConnectorRun saved = runs.saveAndFlush(run);
        saveReceipt(tenant, SYNC, key, hash, "ENTERPRISE_CONNECTOR_RUN", saved.getId());
        publishSync(access, connector, saved, correlationId);
        audit(access, "ENTERPRISE_CONNECTOR_SYNC", "ENTERPRISE_CONNECTOR_RUN", saved.getId(), key,
                Map.of("connectorId", connector.getId(), "processed", run.getProcessed(), "failed", run.getFailedCount()));
        return syncResponse(saved);
    }

    @Override
    @Transactional("postgresTransactionManager")
    public SyncRunResponse retry(UserAccessContext access, String connectorId, String runId, RetryRequest request,
                                 String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureSync();
        RetryRequest safe = Objects.requireNonNull(request, "request");
        EnterpriseConnector connector = findConnector(tenant, required(connectorId, "connectorId", 96));
        EnterpriseConnectorRun run = findRun(tenant, required(runId, "runId", 96));
        if (!connector.getId().equals(run.getConnectorId())) throw new ResourceNotFoundException("connector run not found");
        if (!"FAILED".equals(run.getStatus()) && !"RETRYABLE_FAILED".equals(run.getStatus())) {
            throw new ConflictException("only failed connector runs can be retried");
        }
        if ((run.getRetryCount() == null ? 0 : run.getRetryCount()) >= MAX_RETRIES) {
            throw new ConflictException("connector retry limit reached");
        }
        if (safe.expectedVersion() == null || safe.expectedVersion() != version(run)) {
            throw new ConflictException("connector run changed; reload before retry");
        }
        String key = key(idempotencyKey);
        String hash = EnterpriseConnectorPolicy.sha256("RETRY\u001f" + connector.getId() + "\u001f" + run.getId()
                + "\u001f" + safe.expectedVersion());
        EnterpriseConnectorCommandReceipt replay = begin(tenant, RETRY, key, hash);
        if (replay != null) return syncResponse(findRun(tenant, replay.getAggregateId()));
        EnterpriseConnectorProviderPort.SyncResult result = provider(connector).sync(descriptor(connector), null);
        apply(run, result);
        run.setMode(RETRY);
        run.setRetryCount((run.getRetryCount() == null ? 0 : run.getRetryCount()) + 1);
        EnterpriseConnectorRun saved = runs.saveAndFlush(run);
        saveReceipt(tenant, RETRY, key, hash, "ENTERPRISE_CONNECTOR_RUN", saved.getId());
        publishSync(access, connector, saved, correlationId);
        audit(access, "ENTERPRISE_CONNECTOR_RETRY", "ENTERPRISE_CONNECTOR_RUN", saved.getId(), key,
                Map.of("connectorId", connector.getId(), "retryCount", saved.getRetryCount()));
        return syncResponse(saved);
    }

    @Override
    @Transactional("postgresTransactionManager")
    public DeprovisionResponse deprovision(UserAccessContext access, String connectorId, DeprovisionRequest request,
                                           String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureSync();
        DeprovisionRequest safe = Objects.requireNonNull(request, "request");
        EnterpriseConnector connector = findConnector(tenant, required(connectorId, "connectorId", 96));
        assertVersion(connector, safe.expectedVersion());
        List<String> externalIds = ids(safe.externalIds());
        String reason = required(safe.reason(), "reason", 255);
        String key = key(idempotencyKey);
        String hash = EnterpriseConnectorPolicy.sha256("DEPROVISION\u001f" + connector.getId() + "\u001f"
                + safe.expectedVersion() + "\u001f" + String.join("\u001e", externalIds) + "\u001f" + reason);
        EnterpriseConnectorCommandReceipt replay = begin(tenant, DEPROVISION, key, hash);
        if (replay != null) return deprovisionResponse(findRun(tenant, replay.getAggregateId()));
        EnterpriseConnectorProviderPort.DeprovisionResult result = provider(connector)
                .deprovision(descriptor(connector), externalIds);
        EnterpriseConnectorRun run = newRun(connector, DEPROVISION, result.status(), access.userId());
        run.setProcessed(externalIds.size());
        run.setCreatedCount(result.deprovisioned());
        run.setUpdatedCount(0);
        run.setFailedCount(result.skipped());
        run.setSampleExternalIds(joinSample(externalIds));
        EnterpriseConnectorRun saved = runs.saveAndFlush(run);
        saveReceipt(tenant, DEPROVISION, key, hash, "ENTERPRISE_CONNECTOR_RUN", saved.getId());
        String eventId = IdGenerator.newId();
        publish(new com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorDeprovisioned(eventId, tenant,
                connector.getId(), saved.getId(), access.userId(), version(saved), externalIds.size(),
                result.deprovisioned(), result.skipped(), now(), 1, correlation(correlationId, eventId),
                correlation(correlationId, eventId)));
        audit(access, "ENTERPRISE_CONNECTOR_DEPROVISION", "ENTERPRISE_CONNECTOR_RUN", saved.getId(), key,
                Map.of("connectorId", connector.getId(), "requested", externalIds.size(), "reason", reason));
        return deprovisionResponse(saved);
    }

    private void apply(EnterpriseConnectorRun run, EnterpriseConnectorProviderPort.SyncResult result) {
        run.setProcessed(Math.max(0, result.processed()));
        run.setCreatedCount(Math.max(0, result.created()));
        run.setUpdatedCount(Math.max(0, result.updated()));
        run.setFailedCount(Math.max(0, result.failed()));
        run.setRetryCount(Math.max(0, result.retryCount()));
        run.setStatus(required(result.status(), "status", 32).toUpperCase(Locale.ROOT));
        run.setErrorCode(optional(result.errorCode(), 96));
    }

    private EnterpriseConnectorRun newRun(EnterpriseConnector connector, String mode, String status, String userId) {
        EnterpriseConnectorRun run = new EnterpriseConnectorRun();
        run.setTenantId(connector.getTenantId());
        run.setConnectorId(connector.getId());
        run.setMode(mode);
        run.setStatus(status);
        run.setProcessed(0);
        run.setCreatedCount(0);
        run.setUpdatedCount(0);
        run.setFailedCount(0);
        run.setRetryCount(0);
        run.setRequestedBy(userId);
        return run;
    }

    private EnterpriseConnectorProviderPort provider(EnterpriseConnector connector) {
        return registry.require(connector.getAdapterKey());
    }

    private EnterpriseConnectorProviderPort.ConnectorDescriptor descriptor(EnterpriseConnector connector) {
        return new EnterpriseConnectorProviderPort.ConnectorDescriptor(connector.getProviderKey(),
                ProviderKind.valueOf(connector.getProviderKind()), connector.getMappingVersion(), connector.getMappingHash());
    }

    private EnterpriseConnectorCommandReceipt begin(String tenant, String operation, String key, String hash) {
        Optional<EnterpriseConnectorCommandReceipt> existing = receipts
                .findByTenantIdAndOperationAndIdempotencyKey(tenant, operation, key);
        if (existing.isEmpty()) return null;
        if (!hash.equals(existing.get().getPayloadHash())) throw new ConflictException("idempotency key was reused with another payload");
        return existing.get();
    }

    private void saveReceipt(String tenant, String operation, String key, String hash, String aggregateType,
                             String aggregateId) {
        EnterpriseConnectorCommandReceipt receipt = new EnterpriseConnectorCommandReceipt();
        receipt.setTenantId(tenant);
        receipt.setOperation(operation);
        receipt.setIdempotencyKey(key);
        receipt.setPayloadHash(hash);
        receipt.setAggregateType(aggregateType);
        receipt.setAggregateId(aggregateId);
        receipts.saveAndFlush(receipt);
    }

    private void publish(EnterpriseConnectorEvent event) {
        events.publishEvent(event);
    }

    private void publishSync(UserAccessContext access, EnterpriseConnector connector, EnterpriseConnectorRun run,
                             String correlationId) {
        String eventId = IdGenerator.newId();
        publish(new EnterpriseConnectorSyncCompleted(eventId, connector.getTenantId(), connector.getId(), run.getId(),
                access.userId(), version(run), run.getMode(), run.getProcessed(), run.getCreatedCount(),
                run.getUpdatedCount(), run.getFailedCount(), now(), 1, correlation(correlationId, eventId),
                correlation(correlationId, eventId)));
    }

    private void audit(UserAccessContext access, String action, String resourceType, String resourceId,
                       String key, Map<String, Object> details) {
        Map<String, Object> bounded = new LinkedHashMap<>();
        details.forEach((name, value) -> {
            if (bounded.size() < 12 && name != null && value != null) bounded.put(name, String.valueOf(value));
        });
        audit.record(new AuditRecord(access.tenantId(), access.userId(), "TENANT", access.tenantId(), action,
                resourceType, resourceId, AuditOutcome.SUCCESS, null, null, null, bounded,
                "enterprise-connectors:" + key, now(), null));
    }

    private void ensureWrite() {
        if (!permission("enterprise.connectors.manage")) throw new ForbiddenException("connector management is required");
    }

    private void ensureRead() {
        if (!permission("enterprise.connectors.read") && !permission("enterprise.connectors.manage")) {
            throw new ForbiddenException("connector read permission is required");
        }
    }

    private void ensureSync() {
        if (!permission("enterprise.connectors.sync") && !permission("enterprise.connectors.manage")) {
            throw new ForbiddenException("connector sync permission is required");
        }
    }

    private boolean permission(String expected) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal)) return false;
        if (principal.deniedPermissions().stream().map(this::lower)
                .anyMatch(value -> value.equals("*") || value.equals(expected))) return false;
        return principal.permissions().stream().map(this::lower)
                .anyMatch(value -> value.equals("*") || value.equals(expected));
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String tenant(UserAccessContext access) {
        if (access == null || access.userId() == null || access.userId().isBlank()) {
            throw new ForbiddenException("authenticated access context is required");
        }
        return AuthenticatedTenant.require(access.tenantId());
    }

    private EnterpriseConnector findConnector(String tenant, String id) {
        return connectors.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("enterprise connector not found"));
    }

    private EnterpriseConnectorRun findRun(String tenant, String id) {
        return runs.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("enterprise connector run not found"));
    }

    private void assertVersion(EnterpriseConnector connector, Long expected) {
        if (expected == null || expected != version(connector)) {
            throw new ConflictException("connector changed; reload before running an operation");
        }
    }

    private EnterpriseConnectorPolicy.Mapping mapping(String version, Map<String, String> fieldMapping) {
        try {
            return EnterpriseConnectorPolicy.mapping(version, fieldMapping);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage());
        }
    }

    private String secret(String value) {
        try {
            return EnterpriseConnectorPolicy.secretReference(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage());
        }
    }

    private List<String> ids(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 100) throw new BadRequestException("externalIds must contain 1-100 values");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        values.forEach(value -> unique.add(required(value, "externalId", 96)));
        return List.copyOf(unique);
    }

    private String joinSample(List<String> values) {
        if (values == null) return null;
        return String.join(",", values.stream().filter(Objects::nonNull).map(value -> required(value, "sampleExternalId", 96))
                .limit(MAX_SAMPLE_IDS).toList());
    }

    private String key(String value) {
        return required(value, "idempotencyKey", 255);
    }

    private String required(String value, String name, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new BadRequestException(name + " is required");
        if (normalized.length() > max) throw new BadRequestException(name + " is too long");
        return normalized;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return required(value, "value", max);
    }

    private long version(EnterpriseConnector value) {
        return value.getRecordVersion() == null ? 0 : value.getRecordVersion();
    }

    private long version(EnterpriseConnectorRun value) {
        return value.getRecordVersion() == null ? 0 : value.getRecordVersion();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String correlation(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private ConnectorResponse connectorResponse(EnterpriseConnector value) {
        return new ConnectorResponse(value.getId(), value.getTenantId(), value.getProviderKind(), value.getAdapterKey(),
                value.getProviderKey(), value.getDisplayName(), value.getEndpoint(), "secret://***",
                value.getMappingVersion(), value.getMappingHash(), value.getStatus(), version(value),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private SyncPreviewResponse previewResponse(EnterpriseConnectorRun run) {
        List<String> samples = splitSample(run.getSampleExternalIds());
        return new SyncPreviewResponse(run.getConnectorId(), run.getId(), run.getMode(), run.getCreatedCount(),
                run.getUpdatedCount(), Math.max(0, run.getProcessed() - run.getCreatedCount() - run.getUpdatedCount()),
                run.getProcessed(), samples, run.getStatus(), version(run), run.getCreatedAt());
    }

    private SyncRunResponse syncResponse(EnterpriseConnectorRun run) {
        return new SyncRunResponse(run.getConnectorId(), run.getId(), run.getMode(), run.getProcessed(),
                run.getCreatedCount(), run.getUpdatedCount(), run.getFailedCount(), run.getRetryCount(),
                run.getStatus(), run.getErrorCode(), version(run), run.getCreatedAt(), run.getUpdatedAt());
    }

    private DeprovisionResponse deprovisionResponse(EnterpriseConnectorRun run) {
        return new DeprovisionResponse(run.getConnectorId(), run.getId(), run.getProcessed(), run.getCreatedCount(),
                run.getFailedCount(), run.getStatus(), version(run), run.getCreatedAt());
    }

    private List<String> splitSample(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(",", MAX_SAMPLE_IDS));
    }
}
