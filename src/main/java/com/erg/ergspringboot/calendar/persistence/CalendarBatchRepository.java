package com.erg.ergspringboot.calendar.persistence;

import com.erg.ergspringboot.calendar.persistence.CalendarBatch;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarBatchRepository extends JpaRepository<CalendarBatch, String> {

    Optional<CalendarBatch> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<CalendarBatch> findByTenantIdAndId(String tenantId, String id);

    /** SELECT ... FOR UPDATE: publish, cancel, rename and draft saves of one batch run one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from CalendarBatch b where b.tenantId = :tenantId and b.id = :id")
    Optional<CalendarBatch> lockByTenantIdAndId(@Param("tenantId") String tenantId, @Param("id") String id);

    /** Transaction-scoped advisory lock: callers with the same key run one after another until commit/rollback. */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) t", nativeQuery = true)
    long advisoryXactLock(@Param("key") String key);

    List<CalendarBatch> findByTenantIdAndSchoolId(String tenantId, String schoolId);

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
