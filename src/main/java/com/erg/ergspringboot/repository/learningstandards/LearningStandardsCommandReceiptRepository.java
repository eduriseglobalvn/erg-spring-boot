package com.erg.ergspringboot.repository.learningstandards;

import com.erg.ergspringboot.model.learningstandards.LearningStandardsCommandReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningStandardsCommandReceiptRepository
        extends JpaRepository<LearningStandardsCommandReceipt, String> {
    Optional<LearningStandardsCommandReceipt> findByTenantIdAndIdempotencyKeyAndOperation(
            String tenantId, String idempotencyKey, String operation);
}
