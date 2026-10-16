package com.erg.ergspringboot.repository.notifications;

import com.erg.ergspringboot.model.notifications.NotificationOutboxEntity;
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
     * loop until a partial batch signals the tail is reached. Non-terminal rows (pending/retry/processing/
     * failed) are never touched.
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

    long countByStatus(String status);

    long countByTenantIdAndStatus(String tenantId, String status);
}
