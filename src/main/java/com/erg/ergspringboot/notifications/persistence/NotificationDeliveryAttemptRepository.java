package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.notifications.persistence.NotificationDeliveryAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryAttemptRepository extends JpaRepository<NotificationDeliveryAttemptEntity, String> {
}
