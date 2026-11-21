package com.erg.ergspringboot.learningoutcomes.persistence;

import com.erg.ergspringboot.learningoutcomes.persistence.Competency;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompetencyRepository extends JpaRepository<Competency, String> {
    Optional<Competency> findByTenantIdAndId(String tenantId, String id);
    Optional<Competency> findByTenantIdAndCode(String tenantId, String code);
    List<Competency> findTop100ByTenantIdOrderByUpdatedAtDesc(String tenantId);
    List<Competency> findTop100ByTenantIdAndEducationUnitIdOrderByUpdatedAtDesc(
            String tenantId, String educationUnitId);

    /** Same contract as {@link LearningPathRepository#findScoped}: filter and scope before the page limit. */
    @Query("""
            select c from Competency c
            where c.tenantId = :tenantId
              and (:educationUnitId = '' or c.educationUnitId = :educationUnitId)
              and (:allUnits = true or c.educationUnitId is null or trim(c.educationUnitId) = ''
                   or c.educationUnitId in :unitIds)
            order by c.updatedAt desc
            """)
    List<Competency> findScoped(String tenantId, String educationUnitId, boolean allUnits,
                                Collection<String> unitIds, Pageable page);
}
