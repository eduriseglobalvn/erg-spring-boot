package com.erg.ergspringboot.repository.auth;

import com.erg.ergspringboot.model.auth.SecurityEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, String> {
    long countByTenantIdAndUserIdAndEventTypeAndOccurredAtAfter(
            String tenantId,
            String userId,
            String eventType,
            OffsetDateTime occurredAt
    );

    List<SecurityEvent> findByUserIdAndTenantIdOrderByOccurredAtDesc(String userId, String tenantId);

    List<SecurityEvent> findByTenantIdAndUserIdInOrderByOccurredAtDesc(String tenantId, Collection<String> userIds);

    Page<SecurityEvent> findByTenantIdOrderByOccurredAtDescIdDesc(String tenantId, Pageable pageable);

    Page<SecurityEvent> findByTenantIdAndUserIdOrderByOccurredAtDescIdDesc(
            String tenantId, String userId, Pageable pageable);
}
