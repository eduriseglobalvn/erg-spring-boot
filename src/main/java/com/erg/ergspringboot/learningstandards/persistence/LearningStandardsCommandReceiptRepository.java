package com.erg.ergspringboot.learningstandards.persistence;

import com.erg.ergspringboot.learningstandards.persistence.LearningStandardsCommandReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningStandardsCommandReceiptRepository
        extends JpaRepository<LearningStandardsCommandReceipt, String> {
    Optional<LearningStandardsCommandReceipt> findByTenantIdAndIdempotencyKeyAndOperation(
            String tenantId, String idempotencyKey, String operation);
}
