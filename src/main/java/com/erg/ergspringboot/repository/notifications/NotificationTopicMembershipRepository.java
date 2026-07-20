package com.erg.ergspringboot.repository.notifications;

import com.erg.ergspringboot.model.notifications.NotificationTopicMembershipEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTopicMembershipRepository extends JpaRepository<NotificationTopicMembershipEntity, String> {

    Optional<NotificationTopicMembershipEntity> findByDeviceRegistrationIdAndTopicName(String deviceRegistrationId, String topicName);
}
