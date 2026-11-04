package com.erg.ergspringboot.learningstandards.internal;

import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.learningstandards.persistence.LearningStandardsCommandReceipt;
import com.erg.ergspringboot.learningstandards.persistence.LearningStandardsCommandReceiptRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Command journal for learning-standards commands: idempotency receipts and bounded audit records. */
final class LearningStandardsCommandJournal {

    private final LearningStandardsCommandReceiptRepository receipts;
    private final AuditOperations audit;

    LearningStandardsCommandJournal(LearningStandardsCommandReceiptRepository receipts, AuditOperations audit) {
        this.receipts = receipts;
        this.audit = audit;
    }

    LearningStandardsCommandReceipt begin(String tenant, String operation, String key, String hash) {
        Optional<LearningStandardsCommandReceipt> existing = receipts
                .findByTenantIdAndIdempotencyKeyAndOperation(tenant, key, operation);
        if (existing.isEmpty()) return null;
        if (!hash.equals(existing.get().getPayloadHash())) throw new ConflictException("IDEMPOTENCY_CONFLICT");
        return existing.get();
    }

    void saveReceipt(String tenant, String operation, String key, String hash,
                             String aggregateType, String aggregateId) {
        LearningStandardsCommandReceipt receipt = new LearningStandardsCommandReceipt();
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
                "learning-standards:" + key, LearningStandardsInputs.now(), null));
    }
}
