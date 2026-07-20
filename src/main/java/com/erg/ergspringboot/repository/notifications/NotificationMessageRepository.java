package com.erg.ergspringboot.repository.notifications;

import com.erg.ergspringboot.model.notifications.NotificationMessageEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessageEntity, String> {

    Optional<NotificationMessageEntity> findByTenantIdAndDedupeKey(String tenantId, String dedupeKey);
}
