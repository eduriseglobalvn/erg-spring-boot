package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.notifications.persistence.NotificationDeliveryAttemptEntity;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationDeliveryAttemptRepository extends JpaRepository<NotificationDeliveryAttemptEntity, String> {

    /** Bounded retention purge, one transaction per call; nothing references this table, so no FK ordering applies. */
    @Modifying
    @Transactional
    @Query(
            value = """
                    delete from notification_delivery_attempts
                    where id in (
                        select id
                        from notification_delivery_attempts
                        where created_at < :cutoff
                        limit :batchSize
                    )
                    """,
            nativeQuery = true
    )
    int deleteOlderThan(OffsetDateTime cutoff, int batchSize);
}
