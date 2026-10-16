package com.erg.ergspringboot.model.notifications;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
        name = "notification_recipients",
        indexes = {
                @Index(name = "idx_notification_recipients_inbox", columnList = "tenant_id, user_id, portal, status, created_at"),
                @Index(name = "idx_notification_recipients_unread", columnList = "tenant_id, user_id, portal")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NotificationRecipientEntity extends BaseEntity {

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "notification_id", length = 36, nullable = false)
    private String notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", referencedColumnName = "id", insertable = false, updatable = false)
    @ToString.Exclude
    private NotificationMessageEntity message;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(length = 32, nullable = false)
    private String portal;

    @Column(name = "role_key", length = 64)
    private String roleKey;

    @Column(length = 24, nullable = false)
    private String status = "unread";

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "delivery_policy", length = 24, nullable = false)
    private String deliveryPolicy = "push_and_inbox";
}
