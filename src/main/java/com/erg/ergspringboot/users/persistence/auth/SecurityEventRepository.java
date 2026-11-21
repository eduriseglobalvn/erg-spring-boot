package com.erg.ergspringboot.users.persistence.auth;

import com.erg.ergspringboot.users.persistence.auth.SecurityEvent;
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

    /** The newest risky event per user: replaces scanning every event of the page's users. */
    @org.springframework.data.jpa.repository.Query(value = """
            select distinct on (user_id) user_id as \"userId\", risk_code as \"riskCode\", severity as \"severity\"
            from security_events
            where tenant_id = :tenantId and user_id in (:userIds) and risk_code is not null and risk_code <> ''
            order by user_id, occurred_at desc, id desc
            """, nativeQuery = true)
    List<LatestRisk> findLatestRiskByUserIds(
            @org.springframework.data.repository.query.Param("tenantId") String tenantId,
            @org.springframework.data.repository.query.Param("userIds") Collection<String> userIds);

    interface LatestRisk {
        String getUserId();

        String getRiskCode();

        String getSeverity();
    }

    Page<SecurityEvent> findByTenantIdOrderByOccurredAtDescIdDesc(String tenantId, Pageable pageable);

    Page<SecurityEvent> findByTenantIdAndUserIdOrderByOccurredAtDescIdDesc(
            String tenantId, String userId, Pageable pageable);
}
