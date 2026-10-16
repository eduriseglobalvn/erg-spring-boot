package com.erg.ergspringboot.repository.learningoutcomes;

import com.erg.ergspringboot.model.learningoutcomes.LearningOutcomeCommandReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningOutcomeCommandReceiptRepository extends JpaRepository<LearningOutcomeCommandReceipt, String> {
    Optional<LearningOutcomeCommandReceipt> findByTenantIdAndIdempotencyKeyAndOperation(
            String tenantId, String idempotencyKey, String operation);
}
