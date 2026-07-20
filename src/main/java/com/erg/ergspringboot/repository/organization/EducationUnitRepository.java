package com.erg.ergspringboot.repository.organization;

import com.erg.ergspringboot.model.organization.EducationUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface EducationUnitRepository extends JpaRepository<EducationUnit, String> {

    @Deprecated(forRemoval = false)
    List<EducationUnit> findAllByOrderByNameAsc();

    /** Session bootstrap must remain bounded even for global administrators. */
    List<EducationUnit> findTop500ByOrderByNameAsc();

    @Cacheable(cacheNames = "organizationEducationUnitEntities", key = "#tenantId")
    List<EducationUnit> findByTenantIdOrderByNameAsc(String tenantId);

    List<EducationUnit> findByTenantIdOrderByNameAsc(String tenantId, Pageable pageable);

    List<EducationUnit> findByTenantIdAndIdInOrderByNameAsc(
            String tenantId, Collection<String> ids, Pageable pageable);

    Optional<EducationUnit> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndSlugIgnoreCase(String tenantId, String slug);

    @Query(value = """
            select unit from EducationUnit unit
            where unit.tenantId = :tenantId
              and (:status is null or lower(unit.status) = :status)
              and (:search is null or lower(unit.name) like concat('%', :search, '%')
                   or lower(unit.slug) like concat('%', :search, '%'))
            order by unit.name asc
            """, countQuery = """
            select count(unit.id) from EducationUnit unit
            where unit.tenantId = :tenantId
              and (:status is null or lower(unit.status) = :status)
              and (:search is null or lower(unit.name) like concat('%', :search, '%')
                   or lower(unit.slug) like concat('%', :search, '%'))
            """)
    Page<EducationUnit> findDirectory(String tenantId, String search, String status, Pageable pageable);

    List<EducationUnit> findByIdInOrderByNameAsc(Collection<String> ids);

    List<EducationUnit> findByParentIdInOrderByNameAsc(Collection<String> parentIds);

    List<EducationUnit> findByTenantIdAndParentIdInOrderByNameAsc(
            String tenantId, Collection<String> parentIds, Pageable pageable);
}
