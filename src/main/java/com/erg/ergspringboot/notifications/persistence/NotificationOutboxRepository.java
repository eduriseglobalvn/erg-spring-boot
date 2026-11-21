package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.notifications.persistence.NotificationOutboxEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEntity, String> {

    /**
     * Batch-bounded retention purge: delete terminal rows ('sent'/'dead') older than the cutoff, at most
     * {@code batchSize} per call so the delete never long-locks the busiest table. Each invocation runs in
     * its own transaction (see {@link Transactional}) and returns the affected row count so the caller can
     * loop until a partial batch signals the tail is reached. Non-terminal rows (pending/retry/processing)
     * are never touched.
     */
    @Modifying
    @Transactional
    @Query(
            value = """
                    delete from notification_outbox
                    where id in (
                        select id
                        from notification_outbox
                        where status in ('sent', 'dead')
                          and created_at < :cutoff
                        limit :batchSize
                    )
                    """,
            nativeQuery = true
    )
    int deleteTerminalOlderThan(OffsetDateTime cutoff, int batchSize);

    /** Same bounded purge for terminal 'failed' rows (served by the status prefix of idx_notification_outbox_pickup). */
    @Modifying
    @Transactional
    @Query(
            value = """
                    delete from notification_outbox
                    where id in (
                        select id
                        from notification_outbox
                        where status = 'failed'
                          and created_at < :cutoff
                        limit :batchSize
                    )
                    """,
            nativeQuery = true
    )
    int deleteFailedOlderThan(OffsetDateTime cutoff, int batchSize);

    @Query(
            value = """
                    select *
                    from notification_outbox
                    where (
                        status in ('pending', 'retry')
                        and next_attempt_at <= :now
                    )
                    or (
                        status = 'processing'
                        and (locked_at is null or locked_at <= :staleBefore)
                    )
                    order by next_attempt_at asc, created_at asc
                    for update skip locked
                    limit :pollSize
                    """,
            nativeQuery = true
    )
    List<NotificationOutboxEntity> findDueForUpdate(OffsetDateTime now, OffsetDateTime staleBefore, int pollSize);

    @Query(
            value = """
                    select *
                    from notification_outbox
                    where tenant_id = :tenantId
                      and (
                        (
                            status in ('pending', 'retry')
                            and next_attempt_at <= :now
                        )
                        or (
                            status = 'processing'
                            and (locked_at is null or locked_at <= :staleBefore)
                        )
                      )
                    order by next_attempt_at asc, created_at asc
                    for update skip locked
                    limit :pollSize
                    """,
            nativeQuery = true
    )
    List<NotificationOutboxEntity> findDueForUpdateByTenant(
            String tenantId,
            OffsetDateTime now,
            OffsetDateTime staleBefore,
            int pollSize
    );

    @Query(
            value = """
                    select *
                    from notification_outbox
                    where tenant_id = :tenantId
                      and status = 'dead'
                    order by next_attempt_at asc, created_at asc
                    for update skip locked
                    limit :limit
                    """,
            nativeQuery = true
    )
    List<NotificationOutboxEntity> findDeadForUpdateByTenant(String tenantId, int limit);

    /**
     * Refreshes this worker's lease right before a send so a long batch never lets another instance
     * reclaim a row that is about to be delivered. Returns 0 when the lease was lost.
     */
    @Modifying
    @Transactional
    @Query(
            value = """
                    update notification_outbox
                    set locked_at = :now
                    where id = :id
                      and locked_by = :workerId
                      and status = 'processing'
                    """,
            nativeQuery = true
    )
    int renewLease(String id, String workerId, OffsetDateTime now);

    /** Loads a row for result recording only while this worker still owns its lease. */
    @Query(
            value = """
                    select *
                    from notification_outbox
                    where id = :id
                      and locked_by = :workerId
                      and status = 'processing'
                    for update
                    """,
            nativeQuery = true
    )
    java.util.Optional<NotificationOutboxEntity> findLeasedForUpdate(String id, String workerId);

    /** Returns rows this worker claimed but never started to the queue now, instead of after the lease expires. */
    @Modifying
    @Transactional
    @Query(
            value = """
                    update notification_outbox
                    set status = case when coalesce(attempt_count, 0) > 0 then 'retry' else 'pending' end,
                        locked_at = null,
                        locked_by = null
                    where id in (:ids)
                      and locked_by = :workerId
                      and status = 'processing'
                    """,
            nativeQuery = true
    )
    int releaseLease(java.util.Collection<String> ids, String workerId);

    interface StatusCount {
        String getStatus();

        long getTotal();
    }

    /** One scan for the dashboard counters instead of one count per status. */
    @Query("""
            select o.status as status, count(o) as total
            from NotificationOutboxEntity o
            where o.tenantId = :tenantId and o.status in ('pending', 'retry', 'sent', 'dead')
            group by o.status
            """)
    List<StatusCount> countByTenantGroupedByStatus(@org.springframework.data.repository.query.Param("tenantId") String tenantId);

    long countByStatus(String status);

    long countByTenantIdAndStatus(String tenantId, String status);
}
