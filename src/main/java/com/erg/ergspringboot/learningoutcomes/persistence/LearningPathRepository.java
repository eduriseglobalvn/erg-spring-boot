package com.erg.ergspringboot.learningoutcomes.persistence;

import com.erg.ergspringboot.learningoutcomes.persistence.LearningPath;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LearningPathRepository extends JpaRepository<LearningPath, String> {
    Optional<LearningPath> findByTenantIdAndId(String tenantId, String id);
    List<LearningPath> findTop100ByTenantIdOrderByUpdatedAtDesc(String tenantId);
    List<LearningPath> findTop100ByTenantIdAndStatusOrderByUpdatedAtDesc(String tenantId, String status);

    /**
     * Filters and the caller's scope are applied before the page limit. Blank string parameters mean "no filter";
     * {@code allUnits}/{@code allClasses} mean the caller manages everything, otherwise the id lists must be non-empty.
     */
    @Query("""
            select p from LearningPath p
            where p.tenantId = :tenantId
              and (:status = '' or p.status = :status)
              and (:educationUnitId = '' or p.educationUnitId = :educationUnitId)
              and (:classId = '' or p.classId = :classId)
              and (:allUnits = true or p.educationUnitId is null or trim(p.educationUnitId) = ''
                   or p.educationUnitId in :unitIds)
              and (:allClasses = true or p.classId is null or trim(p.classId) = '' or p.classId in :classIds)
            order by p.updatedAt desc
            """)
    List<LearningPath> findScoped(String tenantId, String status, String educationUnitId, String classId,
                                  boolean allUnits, Collection<String> unitIds,
                                  boolean allClasses, Collection<String> classIds, Pageable page);
}
