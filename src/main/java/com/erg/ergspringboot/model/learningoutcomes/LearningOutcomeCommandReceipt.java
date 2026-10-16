package com.erg.ergspringboot.model.learningoutcomes;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "learning_outcome_command_receipts", indexes = {
        @Index(name = "idx_learning_outcome_receipts_tenant_key", columnList = "tenant_id, idempotency_key, operation", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class LearningOutcomeCommandReceipt extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 96)
    private String tenantId;

    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 96)
    private String aggregateId;
}
