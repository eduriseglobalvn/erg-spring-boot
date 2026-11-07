package com.erg.ergspringboot.timekeeping.persistence;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkSiteRepository extends JpaRepository<TkSite, String> {

    List<TkSite> findByTenantIdAndSchoolIdAndActiveTrueOrderByNameAsc(String tenantId, String schoolId);

    boolean existsByTenantIdAndSchoolIdAndActiveTrue(String tenantId, String schoolId);

    List<TkSite> findByTenantIdAndActiveTrue(String tenantId);

    Optional<TkSite> findByIdAndTenantId(String id, String tenantId);

    List<TkSite> findByTenantIdOrderByNameAsc(String tenantId);

    List<TkSite> findByTenantIdAndSchoolIdOrderByNameAsc(String tenantId, String schoolId);
}
