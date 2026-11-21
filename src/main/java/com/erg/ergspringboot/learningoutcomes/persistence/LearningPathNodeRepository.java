package com.erg.ergspringboot.learningoutcomes.persistence;

import com.erg.ergspringboot.learningoutcomes.persistence.LearningPathNode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LearningPathNodeRepository extends JpaRepository<LearningPathNode, String> {
    Optional<LearningPathNode> findByTenantIdAndId(String tenantId, String id);
    List<LearningPathNode> findAllByTenantIdAndPathIdOrderBySortOrderAsc(String tenantId, String pathId);
    List<LearningPathNode> findTop100ByTenantIdAndPathIdOrderBySortOrderAsc(String tenantId, String pathId);
    boolean existsByTenantIdAndPathId(String tenantId, String pathId);
    long countByTenantIdAndPathId(String tenantId, String pathId);
    boolean existsByTenantIdAndPathIdAndContentRefId(String tenantId, String pathId, String contentRefId);

    @Query("""
            select n.pathId as pathId, count(n.id) as nodeCount
            from LearningPathNode n
            where n.tenantId = :tenantId and n.pathId in :pathIds
            group by n.pathId
            """)
    List<NodeCountProjection> countGroupedByPathId(String tenantId, Collection<String> pathIds);

    interface NodeCountProjection {
        String getPathId();

        long getNodeCount();
    }
}
