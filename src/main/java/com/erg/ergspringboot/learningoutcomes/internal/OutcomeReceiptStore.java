package com.erg.ergspringboot.learningoutcomes.internal;

import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.model.learningoutcomes.LearningOutcomeCommandReceipt;
import com.erg.ergspringboot.repository.learningoutcomes.LearningOutcomeCommandReceiptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Idempotency receipts for learning-outcome commands. {@link #begin} takes a transaction-scoped advisory lock
 * on the tenant/operation/key triple, so it must run inside the caller's transaction.
 */
@Component
public class OutcomeReceiptStore {
    private final LearningOutcomeCommandReceiptRepository receipts;

    @PersistenceContext
    private EntityManager entityManager;

    public OutcomeReceiptStore(LearningOutcomeCommandReceiptRepository receipts) {
        this.receipts = receipts;
    }

    /** Returns the stored receipt for a replay, {@code null} for a first attempt, or fails on a reused key. */
    LearningOutcomeCommandReceipt begin(String tenant, String operation, String key, String hash) {
        lock(tenant + ":" + operation + ":" + key);
        LearningOutcomeCommandReceipt existing = receipts
                .findByTenantIdAndIdempotencyKeyAndOperation(tenant, key, operation).orElse(null);
        if (existing != null && !hash.equals(existing.getPayloadHash())) {
            throw new ConflictException("idempotency key was used for another learning-outcome command");
        }
        return existing;
    }

    void save(String tenant, String operation, String key, String hash, String aggregateType, String aggregateId) {
        LearningOutcomeCommandReceipt receipt = new LearningOutcomeCommandReceipt();
        receipt.setTenantId(tenant);
        receipt.setOperation(operation);
        receipt.setIdempotencyKey(key);
        receipt.setPayloadHash(hash);
        receipt.setAggregateType(aggregateType);
        receipt.setAggregateId(aggregateId);
        receipts.saveAndFlush(receipt);
    }

    private void lock(String lockKey) {
        if (entityManager == null) return;
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
                .setParameter("lockKey", lockKey).getSingleResult();
    }
}
