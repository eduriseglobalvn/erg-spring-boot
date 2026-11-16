package com.erg.ergspringboot.timekeeping.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkTeachingMonthLineRepository extends JpaRepository<TkTeachingMonthLine, String> {

    List<TkTeachingMonthLine> findByTenantIdAndMonth(String tenantId, String month);
}
