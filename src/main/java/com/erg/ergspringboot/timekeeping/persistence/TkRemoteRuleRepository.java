package com.erg.ergspringboot.timekeeping.persistence;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TkRemoteRuleRepository extends JpaRepository<TkRemoteRule, String> {

    List<TkRemoteRule> findByTenantIdAndSchoolIdAndActiveTrue(String tenantId, String schoolId);

    Optional<TkRemoteRule> findByIdAndTenantId(String id, String tenantId);

    List<TkRemoteRule> findByTenantId(String tenantId);

    List<TkRemoteRule> findByTenantIdAndSchoolId(String tenantId, String schoolId);

    boolean existsByTenantIdAndShiftId(String tenantId, String shiftId);

    /** Moves the rules of a replaced shift to its new row (rules carry no history: isRemote is not date-aware). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TkRemoteRule r set r.shiftId = :newShiftId where r.tenantId = :tenantId and r.shiftId = :oldShiftId")
    int repointShift(@Param("tenantId") String tenantId, @Param("oldShiftId") String oldShiftId,
            @Param("newShiftId") String newShiftId);
}
