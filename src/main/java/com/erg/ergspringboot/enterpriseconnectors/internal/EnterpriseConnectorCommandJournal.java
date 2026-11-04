package com.erg.ergspringboot.enterpriseconnectors.internal;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnector;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorCommandReceipt;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorCommandReceiptRepository;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRun;
import com.erg.ergspringboot.enterpriseconnectors.persistence.EnterpriseConnectorRunRepository;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Command journal for connector commands: idempotency receipts, the short claim transaction that
 * replays or creates a RUNNING run, the failure record of a run whose provider call threw, and the
 * bounded audit record of each accepted command.
 */
final class EnterpriseConnectorCommandJournal {
    static final String RETRYABLE_FAILED = "RETRYABLE_FAILED";

    record Claim(EnterpriseConnector connector, EnterpriseConnectorRun run, boolean replay) {
    }

    private final EnterpriseConnectorCommandReceiptRepository receipts;
    private final EnterpriseConnectorRunRepository runs;
    private final AuditOperations audit;
    private final TransactionTemplate transaction;

    EnterpriseConnectorCommandJournal(EnterpriseConnectorCommandReceiptRepository receipts,
                                      EnterpriseConnectorRunRepository runs, AuditOperations audit,
                                      TransactionTemplate transaction) {
        this.receipts = receipts;
        this.runs = runs;
        this.audit = audit;
        this.transaction = transaction;
    }

    /** Short transaction: replay an existing receipt, or create the RUNNING run and its receipt. */
    Claim claim(String tenant, String operation, String key, String hash, Supplier<Claim> create) {
        try {
            return transaction.execute(status -> {
                EnterpriseConnectorCommandReceipt replay = begin(tenant, operation, key, hash);
                if (replay != null) return new Claim(null, findRun(tenant, replay.getAggregateId()), true);
                Claim created = create.get();
                saveReceipt(tenant, operation, key, hash, "ENTERPRISE_CONNECTOR_RUN", created.run().getId());
                return created;
            });
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent request claimed the same key (receipt) or the same parent run (retry).
            return transaction.execute(status -> {
                EnterpriseConnectorCommandReceipt replay = begin(tenant, operation, key, hash);
                if (replay == null) throw new ConflictException("connector run is already being processed");
                return new Claim(null, findRun(tenant, replay.getAggregateId()), true);
            });
        }
    }

    EnterpriseConnectorCommandReceipt begin(String tenant, String operation, String key, String hash) {
        Optional<EnterpriseConnectorCommandReceipt> existing = receipts
                .findByTenantIdAndOperationAndIdempotencyKey(tenant, operation, key);
        if (existing.isEmpty()) return null;
        if (!hash.equals(existing.get().getPayloadHash())) throw new ConflictException("idempotency key was reused with another payload");
        return existing.get();
    }

    void saveReceipt(String tenant, String operation, String key, String hash, String aggregateType,
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

    void audit(UserAccessContext access, String action, String resourceType, String resourceId,
               String key, Map<String, Object> details) {
        Map<String, Object> bounded = new LinkedHashMap<>();
        details.forEach((name, value) -> {
            if (bounded.size() < 12 && name != null && value != null) bounded.put(name, String.valueOf(value));
        });
        audit.record(new AuditRecord(access.tenantId(), access.userId(), "TENANT", access.tenantId(), action,
                resourceType, resourceId, AuditOutcome.SUCCESS, null, null, null, bounded,
                "enterprise-connectors:" + key, OffsetDateTime.now(ZoneOffset.UTC), null));
    }

    /** A provider exception leaves a retryable failed run instead of a stuck RUNNING row. */
    EnterpriseConnectorRun recordProviderFailure(String tenant, String runId) {
        return transaction.execute(status -> {
            EnterpriseConnectorRun run = findRun(tenant, runId);
            run.setStatus(RETRYABLE_FAILED);
            run.setErrorCode("PROVIDER_ERROR");
            return runs.saveAndFlush(run);
        });
    }

    EnterpriseConnectorRun newRun(EnterpriseConnector connector, String mode, String status, String userId) {
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

    private EnterpriseConnectorRun findRun(String tenant, String id) {
        return runs.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("enterprise connector run not found"));
    }
}
