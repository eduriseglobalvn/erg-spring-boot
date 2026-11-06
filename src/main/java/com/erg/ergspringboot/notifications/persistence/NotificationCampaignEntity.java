package com.erg.ergspringboot.notifications.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.notifications.CampaignStatus;
import com.erg.ergspringboot.notifications.NotificationPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "notification_campaigns",
        indexes = {
                @Index(name = "idx_notification_campaigns_tenant_status_scheduled", columnList = "tenant_id, status, scheduled_at"),
                @Index(name = "idx_notification_campaigns_tenant_creator_created", columnList = "tenant_id, created_by, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationCampaignEntity extends BaseEntity {

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "created_by", length = 36, nullable = false)
    private String createdBy;

    /** ADMIN or TEACHER. */
    @Column(name = "sender_role", length = 16, nullable = false)
    private String senderRole;

    @Column(length = 120, nullable = false)
    private String title;

    @Column(length = 2000, nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private NotificationPriority priority = NotificationPriority.INFO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private CampaignAudience audience;

    @Column(name = "resolved_count")
    private Integer resolvedCount;

    @Column(name = "estimated_count")
    private Integer estimatedCount;

    @Column(name = "require_read_ack", nullable = false)
    private boolean requireReadAck;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;
}
