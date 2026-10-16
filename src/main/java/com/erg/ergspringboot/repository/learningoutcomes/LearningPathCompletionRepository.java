package com.erg.ergspringboot.repository.learningoutcomes;

import com.erg.ergspringboot.model.learningoutcomes.LearningPathCompletion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningPathCompletionRepository extends JpaRepository<LearningPathCompletion, String> {
    Optional<LearningPathCompletion> findByTenantIdAndId(String tenantId, String id);

    Optional<LearningPathCompletion> findByTenantIdAndLearnerIdAndLearningPathId(
            String tenantId, String learnerId, String learningPathId);

    List<LearningPathCompletion> findTop100ByTenantIdAndLearnerIdOrderByEvaluatedAtDesc(
            String tenantId, String learnerId);
}
