package com.erg.ergspringboot.repository.notifications;

import com.erg.ergspringboot.model.notifications.NotificationOutboxEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEntity, String> {

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

    long countByStatus(String status);

    long countByTenantIdAndStatus(String tenantId, String status);
}
