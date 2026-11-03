package com.erg.ergspringboot.repository.outbox;

import com.erg.ergspringboot.model.outbox.OutboxEvent;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    Optional<OutboxEvent> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    @Query(value = """
            select * from outbox_events
            where (status in ('pending', 'retry') and available_at <= :now)
               or (status = 'processing' and lease_until < :now)
            order by available_at asc, created_at asc
            for update skip locked
            limit :limit
            """, nativeQuery = true)
    List<OutboxEvent> findDueForUpdate(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    /** Stamps the claiming worker on rows claimed in the current transaction (V133 column). */
    @Modifying
    @Query(value = "update outbox_events set locked_by = :workerId where id in (:ids)", nativeQuery = true)
    int markLockedBy(@Param("ids") Collection<String> ids, @Param("workerId") String workerId);

    /** Extends the lease right before dispatch; 0 means another worker reclaimed the row. */
    @Modifying
    @Query(value = """
            update outbox_events set lease_until = :leaseUntil
            where id = :id and locked_by = :workerId and status = 'processing'
            """, nativeQuery = true)
    int renewLease(@Param("id") String id, @Param("workerId") String workerId,
            @Param("leaseUntil") OffsetDateTime leaseUntil);

    /** Loads a row for result recording only while {@code workerId} still owns its lease. */
    @Query(value = """
            select * from outbox_events
            where id = :id and locked_by = :workerId and status = 'processing'
            for update
            """, nativeQuery = true)
    Optional<OutboxEvent> findLeasedForUpdate(@Param("id") String id, @Param("workerId") String workerId);
}
