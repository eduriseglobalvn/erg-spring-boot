package com.erg.ergspringboot.enterpriseconnectors.internal;

import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorAccessGuard.ensureRead;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorAccessGuard.ensureSync;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorAccessGuard.ensureWrite;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorAccessGuard.tenant;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorInputs.ids;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorInputs.joinSample;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorInputs.key;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorInputs.mapping;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorInputs.optional;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorInputs.required;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorInputs.secret;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorResponseMapper.connectorResponse;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorResponseMapper.deprovisionResponse;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorResponseMapper.previewResponse;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorResponseMapper.syncResponse;
import static com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorResponseMapper.version;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorDeprovisioned;
import com.erg.ergspringboot.enterpriseconnectors.EnterpriseConnectorEvent;
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
import com.erg.ergspringboot.enterpriseconnectors.internal.EnterpriseConnectorCommandJournal.Claim;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnector;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorCommandReceipt;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRun;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorCommandReceiptRepository;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRepository;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRunRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    /** Claimed run whose provider call is in flight (V122); replays of the same key return it. */
    private static final String RUNNING = "RUNNING";
    private static final String FAILED = "FAILED";
    private static final String RETRYABLE_FAILED = EnterpriseConnectorCommandJournal.RETRYABLE_FAILED;
    private static final int MAX_RETRIES = 3;

    private final EnterpriseConnectorRepository connectors;
    private final EnterpriseConnectorRunRepository runs;
    private final EnterpriseConnectorProviderRegistry registry;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final EnterpriseConnectorCommandJournal journal;

    @Autowired
    public EnterpriseConnectorService(
            EnterpriseConnectorRepository connectors,
            EnterpriseConnectorRunRepository runs,
            EnterpriseConnectorCommandReceiptRepository receipts,
            EnterpriseConnectorProviderRegistry registry,
            ApplicationEventPublisher events,
            AuditOperations audit,
            @Qualifier("postgresTransactionManager") PlatformTransactionManager transactionManager) {
        this.connectors = connectors;
        this.runs = runs;
        this.registry = registry;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.journal = new EnterpriseConnectorCommandJournal(receipts, runs, audit, transaction);
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
        EnterpriseConnectorCommandReceipt replay = journal.begin(tenant, "REGISTER_CONNECTOR", key, hash);
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
        journal.saveReceipt(tenant, "REGISTER_CONNECTOR", key, hash, "ENTERPRISE_CONNECTOR", saved.getId());
        String eventId = IdGenerator.newId();
        publish(new EnterpriseConnectorRegistered(eventId, tenant, saved.getId(), access.userId(), version(saved),
                now(), 1, correlation(correlationId, eventId), correlation(correlationId, eventId)));
        journal.audit(access, "ENTERPRISE_CONNECTOR_REGISTERED", "ENTERPRISE_CONNECTOR", saved.getId(), key,
                Map.of("providerKind", providerKind.name(), "adapterKey", adapterKey, "mappingHash", mapping.hash()));
        return connectorResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public List<ConnectorResponse> connectors(UserAccessContext access) {
        String tenant = tenant(access);
        ensureRead();
        return connectors.findTop100ByTenantIdOrderByUpdatedAtDesc(tenant).stream()
                .map(EnterpriseConnectorResponseMapper::connectorResponse).toList();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "postgresTransactionManager")
    public ConnectorResponse connectorById(UserAccessContext access, String connectorId) {
        String tenant = tenant(access);
        ensureRead();
        return connectorResponse(findConnector(tenant, required(connectorId, "connectorId", 96)));
    }

    /*
     * Provider-backed commands follow claim -> call -> record (SQ-08/C2): a short transaction
     * replays or claims a RUNNING run plus its idempotency receipt, the provider is called with
     * no database transaction or connection held, and a second short transaction records the
     * outcome. Replays of a key whose provider call is still in flight return the RUNNING run.
     */

    @Override
    public SyncPreviewResponse dryRun(UserAccessContext access, String connectorId, RunRequest request,
                                      String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureSync();
        RunRequest safe = Objects.requireNonNull(request, "request");
        String connectorRef = required(connectorId, "connectorId", 96);
        String key = key(idempotencyKey);
        String hash = EnterpriseConnectorPolicy.sha256("DRY_RUN\u001f" + connectorRef + "\u001f"
                + safe.expectedVersion() + "\u001f" + optional(safe.cursor(), 96));
        Claim claim = journal.claim(tenant, DRY_RUN, key, hash, () -> {
            EnterpriseConnector connector = findConnector(tenant, connectorRef);
            assertVersion(connector, safe.expectedVersion());
            return new Claim(connector, runs.saveAndFlush(journal.newRun(connector, DRY_RUN, RUNNING, access.userId())), false);
        });
        if (claim.replay()) return previewResponse(claim.run());
        EnterpriseConnectorProviderPort.PreviewResult result;
        try {
            result = provider(claim.connector()).preview(descriptor(claim.connector()), safe.cursor());
        } catch (RuntimeException failure) {
            return previewResponse(journal.recordProviderFailure(tenant, claim.run().getId()));
        }
        return transaction.execute(status -> {
            EnterpriseConnectorRun run = findRun(tenant, claim.run().getId());
            run.setStatus(PREVIEW);
            run.setProcessed(result.additions() + result.updates() + result.removals());
            run.setCreatedCount(result.additions());
            run.setUpdatedCount(result.updates());
            run.setFailedCount(0);
            run.setSampleExternalIds(joinSample(result.sampleExternalIds()));
            EnterpriseConnectorRun saved = runs.saveAndFlush(run);
            journal.audit(access, "ENTERPRISE_CONNECTOR_DRY_RUN", "ENTERPRISE_CONNECTOR_RUN", saved.getId(), key,
                    Map.of("connectorId", claim.connector().getId(), "total", saved.getProcessed()));
            return previewResponse(saved);
        });
    }

    @Override
    public SyncRunResponse sync(UserAccessContext access, String connectorId, RunRequest request,
                                String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureSync();
        RunRequest safe = Objects.requireNonNull(request, "request");
        String connectorRef = required(connectorId, "connectorId", 96);
        String key = key(idempotencyKey);
        String hash = EnterpriseConnectorPolicy.sha256("SYNC\u001f" + connectorRef + "\u001f"
                + safe.expectedVersion() + "\u001f" + optional(safe.cursor(), 96));
        Claim claim = journal.claim(tenant, SYNC, key, hash, () -> {
            EnterpriseConnector connector = findConnector(tenant, connectorRef);
            assertVersion(connector, safe.expectedVersion());
            return new Claim(connector, runs.saveAndFlush(journal.newRun(connector, SYNC, RUNNING, access.userId())), false);
        });
        if (claim.replay()) return syncResponse(claim.run());
        return syncResponse(callAndRecordSync(access, tenant, claim, safe.cursor(), correlationId, key,
                "ENTERPRISE_CONNECTOR_SYNC"));
    }

    /**
     * Retries a failed run by creating a new RETRY run linked through {@code parent_run_id}; the
     * failed run keeps its own counters (N4). The idempotency replay is checked before any state
     * check, so a lost response is replayed instead of returning 409 (N4). The retry counter is the
     * parent's count + 1 and never comes from the adapter, so MAX_RETRIES is enforced (C2).
     */
    @Override
    public SyncRunResponse retry(UserAccessContext access, String connectorId, String runId, RetryRequest request,
                                 String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureSync();
        RetryRequest safe = Objects.requireNonNull(request, "request");
        String connectorRef = required(connectorId, "connectorId", 96);
        String runRef = required(runId, "runId", 96);
        String key = key(idempotencyKey);
        String hash = EnterpriseConnectorPolicy.sha256("RETRY\u001f" + connectorRef + "\u001f" + runRef
                + "\u001f" + safe.expectedVersion());
        Claim claim = journal.claim(tenant, RETRY, key, hash, () -> {
            EnterpriseConnector connector = findConnector(tenant, connectorRef);
            EnterpriseConnectorRun parent = findRun(tenant, runRef);
            if (!connector.getId().equals(parent.getConnectorId())) {
                throw new ResourceNotFoundException("connector run not found");
            }
            if (!FAILED.equals(parent.getStatus()) && !RETRYABLE_FAILED.equals(parent.getStatus())) {
                throw new ConflictException("only failed connector runs can be retried");
            }
            int previous = parent.getRetryCount() == null ? 0 : parent.getRetryCount();
            if (previous >= MAX_RETRIES) {
                throw new ConflictException("connector retry limit reached");
            }
            if (safe.expectedVersion() == null || safe.expectedVersion() != version(parent)) {
                throw new ConflictException("connector run changed; reload before retry");
            }
            EnterpriseConnectorRun retry = journal.newRun(connector, RETRY, RUNNING, access.userId());
            retry.setParentRunId(parent.getId());
            retry.setRetryCount(previous + 1);
            return new Claim(connector, runs.saveAndFlush(retry), false);
        });
        if (claim.replay()) return syncResponse(claim.run());
        return syncResponse(callAndRecordSync(access, tenant, claim, null, correlationId, key,
                "ENTERPRISE_CONNECTOR_RETRY"));
    }

    @Override
    public DeprovisionResponse deprovision(UserAccessContext access, String connectorId, DeprovisionRequest request,
                                           String idempotencyKey, String correlationId) {
        String tenant = tenant(access);
        ensureSync();
        DeprovisionRequest safe = Objects.requireNonNull(request, "request");
        String connectorRef = required(connectorId, "connectorId", 96);
        List<String> externalIds = ids(safe.externalIds());
        String reason = required(safe.reason(), "reason", 255);
        String key = key(idempotencyKey);
        String hash = EnterpriseConnectorPolicy.sha256("DEPROVISION\u001f" + connectorRef + "\u001f"
                + safe.expectedVersion() + "\u001f" + String.join("\u001e", externalIds) + "\u001f" + reason);
        Claim claim = journal.claim(tenant, DEPROVISION, key, hash, () -> {
            EnterpriseConnector connector = findConnector(tenant, connectorRef);
            assertVersion(connector, safe.expectedVersion());
            return new Claim(connector, runs.saveAndFlush(journal.newRun(connector, DEPROVISION, RUNNING, access.userId())), false);
        });
        if (claim.replay()) return deprovisionResponse(claim.run());
        EnterpriseConnectorProviderPort.DeprovisionResult result;
        try {
            result = provider(claim.connector()).deprovision(descriptor(claim.connector()), externalIds);
        } catch (RuntimeException failure) {
            return deprovisionResponse(journal.recordProviderFailure(tenant, claim.run().getId()));
        }
        return transaction.execute(status -> {
            EnterpriseConnectorRun run = findRun(tenant, claim.run().getId());
            run.setStatus(required(result.status(), "status", 32).toUpperCase(Locale.ROOT));
            run.setProcessed(externalIds.size());
            run.setCreatedCount(result.deprovisioned());
            run.setUpdatedCount(0);
            run.setFailedCount(result.skipped());
            run.setSampleExternalIds(joinSample(externalIds));
            EnterpriseConnectorRun saved = runs.saveAndFlush(run);
            String eventId = IdGenerator.newId();
            publish(new EnterpriseConnectorDeprovisioned(eventId, tenant,
                    claim.connector().getId(), saved.getId(), access.userId(), version(saved), externalIds.size(),
                    result.deprovisioned(), result.skipped(), now(), 1, correlation(correlationId, eventId),
                    correlation(correlationId, eventId)));
            journal.audit(access, "ENTERPRISE_CONNECTOR_DEPROVISION", "ENTERPRISE_CONNECTOR_RUN", saved.getId(), key,
                    Map.of("connectorId", claim.connector().getId(), "requested", externalIds.size(), "reason", reason));
            return deprovisionResponse(saved);
        });
    }

    private EnterpriseConnectorRun callAndRecordSync(UserAccessContext access, String tenant, Claim claim,
                                                     String cursor, String correlationId, String key, String action) {
        EnterpriseConnectorProviderPort.SyncResult result;
        try {
            result = provider(claim.connector()).sync(descriptor(claim.connector()), cursor);
        } catch (RuntimeException failure) {
            return journal.recordProviderFailure(tenant, claim.run().getId());
        }
        return transaction.execute(status -> {
            EnterpriseConnectorRun run = findRun(tenant, claim.run().getId());
            apply(run, result);
            EnterpriseConnectorRun saved = runs.saveAndFlush(run);
            publishSync(access, claim.connector(), saved, correlationId);
            journal.audit(access, action, "ENTERPRISE_CONNECTOR_RUN", saved.getId(), key,
                    Map.of("connectorId", claim.connector().getId(), "processed", saved.getProcessed(),
                            "failed", saved.getFailedCount(), "retryCount", saved.getRetryCount()));
            return saved;
        });
    }

    /** Applies provider counters; the retry counter is owned by this service, never by the adapter (C2). */
    private void apply(EnterpriseConnectorRun run, EnterpriseConnectorProviderPort.SyncResult result) {
        run.setProcessed(Math.max(0, result.processed()));
        run.setCreatedCount(Math.max(0, result.created()));
        run.setUpdatedCount(Math.max(0, result.updated()));
        run.setFailedCount(Math.max(0, result.failed()));
        run.setStatus(required(result.status(), "status", 32).toUpperCase(Locale.ROOT));
        run.setErrorCode(optional(result.errorCode(), 96));
    }

    private EnterpriseConnectorProviderPort provider(EnterpriseConnector connector) {
        return registry.require(connector.getAdapterKey());
    }

    private EnterpriseConnectorProviderPort.ConnectorDescriptor descriptor(EnterpriseConnector connector) {
        return new EnterpriseConnectorProviderPort.ConnectorDescriptor(connector.getProviderKey(),
                ProviderKind.valueOf(connector.getProviderKind()), connector.getMappingVersion(), connector.getMappingHash());
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

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String correlation(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
