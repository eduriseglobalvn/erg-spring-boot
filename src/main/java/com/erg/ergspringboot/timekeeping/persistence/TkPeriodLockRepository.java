package com.erg.ergspringboot.timekeeping.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TkPeriodLockRepository extends JpaRepository<TkPeriodLock, String> {

    /** A lock counts until an admin reopens it. */
    boolean existsByTenantIdAndSchoolIdAndPeriodAndReopenedAtIsNull(String tenantId, String schoolId, String period);
}
