package com.erg.ergspringboot.timekeeping.persistence;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkRemoteRuleRepository extends JpaRepository<TkRemoteRule, String> {

    List<TkRemoteRule> findByTenantIdAndSchoolIdAndActiveTrue(String tenantId, String schoolId);

    Optional<TkRemoteRule> findByIdAndTenantId(String id, String tenantId);

    List<TkRemoteRule> findByTenantId(String tenantId);

    List<TkRemoteRule> findByTenantIdAndSchoolId(String tenantId, String schoolId);

    boolean existsByTenantIdAndShiftId(String tenantId, String shiftId);
}
