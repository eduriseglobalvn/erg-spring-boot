package com.erg.ergspringboot.repository.learningoutcomes;

import com.erg.ergspringboot.model.learningoutcomes.CompetencyAttainment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyAttainmentRepository extends JpaRepository<CompetencyAttainment, String> {
    Optional<CompetencyAttainment> findByTenantIdAndId(String tenantId, String id);
    Optional<CompetencyAttainment> findByTenantIdAndLearnerIdAndCompetencyIdAndLearningPathId(
            String tenantId, String learnerId, String competencyId, String learningPathId);
    List<CompetencyAttainment> findTop100ByTenantIdAndLearnerIdOrderByAchievedAtDesc(
            String tenantId, String learnerId);
    List<CompetencyAttainment> findAllByTenantIdAndLearnerIdOrderByAchievedAtDesc(
            String tenantId, String learnerId);
}
