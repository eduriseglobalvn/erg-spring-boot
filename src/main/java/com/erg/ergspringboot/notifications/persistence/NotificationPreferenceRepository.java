package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.notifications.persistence.NotificationPreferenceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, String> {

    List<NotificationPreferenceEntity> findByTenantIdAndUserIdAndPortalOrderByEventTypeAsc(
            String tenantId,
            String userId,
            String portal
    );

    Optional<NotificationPreferenceEntity> findByTenantIdAndUserIdAndPortalAndEventType(
            String tenantId,
            String userId,
            String portal,
            String eventType
    );

    List<NotificationPreferenceEntity> findByTenantIdAndEventTypeAndUserIdIn(
            String tenantId,
            String eventType,
            java.util.Collection<String> userIds
    );

    List<NotificationPreferenceEntity> findByTenantIdAndPortalAndEventTypeAndUserIdIn(
            String tenantId,
            String portal,
            String eventType,
            java.util.Collection<String> userIds
    );

    List<NotificationPreferenceEntity> findByTenantIdAndUserIdAndPortalAndEventTypeIn(
            String tenantId,
            String userId,
            String portal,
            java.util.Collection<String> eventTypes
    );
}
