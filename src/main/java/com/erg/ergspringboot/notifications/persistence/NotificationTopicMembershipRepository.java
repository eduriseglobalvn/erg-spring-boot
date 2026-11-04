package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.notifications.persistence.NotificationTopicMembershipEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTopicMembershipRepository extends JpaRepository<NotificationTopicMembershipEntity, String> {

    Optional<NotificationTopicMembershipEntity> findByDeviceRegistrationIdAndTopicName(String deviceRegistrationId, String topicName);
}
