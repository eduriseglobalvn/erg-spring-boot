package com.erg.ergspringboot.timekeeping.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkTeachingMonthRepository extends JpaRepository<TkTeachingMonth, String> {

    Optional<TkTeachingMonth> findByTenantIdAndMonth(String tenantId, String month);
}
