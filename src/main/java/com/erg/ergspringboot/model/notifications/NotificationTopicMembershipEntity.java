package com.erg.ergspringboot.model.notifications;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "notification_topic_memberships",
        indexes = {
                @Index(name = "idx_notification_topic_name", columnList = "tenant_id, topic_name, membership_status")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NotificationTopicMembershipEntity extends BaseEntity {

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "device_registration_id", length = 36, nullable = false)
    private String deviceRegistrationId;

    @Column(name = "topic_name", length = 255, nullable = false)
    private String topicName;

    @Column(name = "membership_status", length = 24, nullable = false)
    private String membershipStatus = "active";

    @Column(name = "subscribed_at")
    private OffsetDateTime subscribedAt;

    @Column(name = "unsubscribed_at")
    private OffsetDateTime unsubscribedAt;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;
}
