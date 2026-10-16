package com.erg.ergspringboot.model.notifications;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "notification_messages",
        indexes = {
                @Index(name = "idx_notification_messages_tenant_created", columnList = "tenant_id, created_at"),
                @Index(name = "idx_notification_messages_source", columnList = "tenant_id, source_type, source_id"),
                @Index(name = "idx_notification_messages_created_by", columnList = "created_by")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NotificationMessageEntity extends BaseEntity {

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "event_type", length = 80, nullable = false)
    private String eventType;

    @Column(name = "source_type", length = 80)
    private String sourceType;

    @Column(name = "source_id", length = 80)
    private String sourceId;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(name = "body_preview", nullable = false, length = 500)
    private String bodyPreview;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @Column(nullable = false, length = 24)
    private String priority = "normal";

    @Column(nullable = false, length = 24)
    private String channel = "push";

    @Column(nullable = false, length = 24)
    private String visibility = "private";

    @Column(name = "collapse_key", length = 120)
    private String collapseKey;

    @Column(name = "dedupe_key", length = 180)
    private String dedupeKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> payloadData = Map.of();

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}
