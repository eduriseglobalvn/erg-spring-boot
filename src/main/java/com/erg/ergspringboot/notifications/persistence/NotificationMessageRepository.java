package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.notifications.persistence.NotificationMessageEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessageEntity, String> {

    Optional<NotificationMessageEntity> findByTenantIdAndDedupeKey(String tenantId, String dedupeKey);
}
