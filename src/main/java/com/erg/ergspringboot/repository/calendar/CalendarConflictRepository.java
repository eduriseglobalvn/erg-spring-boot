package com.erg.ergspringboot.repository.calendar;

import com.erg.ergspringboot.model.calendar.CalendarConflict;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarConflictRepository extends JpaRepository<CalendarConflict, String> {

    List<CalendarConflict> findByTenantIdAndBatchId(String tenantId, String batchId);

    List<CalendarConflict> findByTenantIdAndBatchIdAndResolved(String tenantId, String batchId, boolean resolved);

    void deleteByTenantIdAndBatchId(String tenantId, String batchId);
}
