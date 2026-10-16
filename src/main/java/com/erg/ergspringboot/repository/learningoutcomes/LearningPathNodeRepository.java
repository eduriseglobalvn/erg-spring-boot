package com.erg.ergspringboot.repository.learningoutcomes;

import com.erg.ergspringboot.model.learningoutcomes.LearningPathNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningPathNodeRepository extends JpaRepository<LearningPathNode, String> {
    Optional<LearningPathNode> findByTenantIdAndId(String tenantId, String id);
    List<LearningPathNode> findAllByTenantIdAndPathIdOrderBySortOrderAsc(String tenantId, String pathId);
    List<LearningPathNode> findTop100ByTenantIdAndPathIdOrderBySortOrderAsc(String tenantId, String pathId);
    boolean existsByTenantIdAndPathId(String tenantId, String pathId);
    long countByTenantIdAndPathId(String tenantId, String pathId);
    boolean existsByTenantIdAndPathIdAndContentRefId(String tenantId, String pathId, String contentRefId);
}
