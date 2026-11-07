package com.erg.ergspringboot.timekeeping.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkPolicyRepository extends JpaRepository<TkPolicy, String> {

    /** The policy in force on {@code date}: the latest one that started on or before it. */
    Optional<TkPolicy> findFirstByTenantIdAndSchoolIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            String tenantId, String schoolId, LocalDate date);

    Optional<TkPolicy> findFirstByTenantIdAndSchoolIdAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(
            String tenantId, String schoolId, LocalDate date);

    Optional<TkPolicy> findByTenantIdAndSchoolIdAndEffectiveFrom(String tenantId, String schoolId, LocalDate date);
}
