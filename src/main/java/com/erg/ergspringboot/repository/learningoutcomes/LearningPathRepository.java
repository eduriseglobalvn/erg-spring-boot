package com.erg.ergspringboot.repository.learningoutcomes;

import com.erg.ergspringboot.model.learningoutcomes.LearningPath;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningPathRepository extends JpaRepository<LearningPath, String> {
    Optional<LearningPath> findByTenantIdAndId(String tenantId, String id);
    List<LearningPath> findTop100ByTenantIdOrderByUpdatedAtDesc(String tenantId);
    List<LearningPath> findTop100ByTenantIdAndStatusOrderByUpdatedAtDesc(String tenantId, String status);
}
