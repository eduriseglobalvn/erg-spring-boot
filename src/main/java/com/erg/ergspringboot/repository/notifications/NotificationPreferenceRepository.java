package com.erg.ergspringboot.repository.notifications;

import com.erg.ergspringboot.model.notifications.NotificationPreferenceEntity;
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

    List<NotificationPreferenceEntity> findByTenantIdAndUserIdAndPortalAndEventTypeIn(
            String tenantId,
            String userId,
            String portal,
            java.util.Collection<String> eventTypes
    );
}
