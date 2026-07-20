package com.erg.ergspringboot.repository.outbox;

import com.erg.ergspringboot.model.outbox.OutboxEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
