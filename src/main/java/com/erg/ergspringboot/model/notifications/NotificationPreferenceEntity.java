package com.erg.ergspringboot.model.notifications;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "notification_preferences",
        indexes = {
                @Index(name = "uq_notification_preferences_user_event", columnList = "tenant_id, user_id, portal, event_type", unique = true)
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NotificationPreferenceEntity extends BaseEntity {

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(length = 32, nullable = false)
    private String portal;

    @Column(name = "event_type", length = 80, nullable = false)
    private String eventType;

    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = true;

    @Column(name = "inbox_enabled", nullable = false)
    private Boolean inboxEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quiet_hours", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> quietHours = Map.of();
}
