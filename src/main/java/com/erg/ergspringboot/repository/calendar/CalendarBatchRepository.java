package com.erg.ergspringboot.repository.calendar;

import com.erg.ergspringboot.model.calendar.CalendarBatch;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarBatchRepository extends JpaRepository<CalendarBatch, String> {

    Optional<CalendarBatch> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<CalendarBatch> findByTenantIdAndId(String tenantId, String id);

    @Query(
            value = """
                    select batch
                    from CalendarBatch batch
                    where batch.tenantId = :tenantId
                      and (:schoolId is null or batch.schoolId = :schoolId)
                      and (:status is null or upper(batch.status) = :status)
                    """,
            countQuery = """
                    select count(batch.id)
                    from CalendarBatch batch
                    where batch.tenantId = :tenantId
                      and (:schoolId is null or batch.schoolId = :schoolId)
                      and (:status is null or upper(batch.status) = :status)
                    """
    )
    Page<CalendarBatch> findTeachingScheduleBatchPage(
            @Param("tenantId") String tenantId,
            @Param("schoolId") String schoolId,
            @Param("status") String status,
            Pageable pageable);
}
