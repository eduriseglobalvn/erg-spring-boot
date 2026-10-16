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
        name = "notification_outbox",
        indexes = {
                @Index(name = "idx_notification_outbox_pickup", columnList = "status, next_attempt_at, created_at"),
                @Index(name = "idx_notification_outbox_notification", columnList = "notification_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NotificationOutboxEntity extends BaseEntity {

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "notification_id", length = 36, nullable = false)
    private String notificationId;

    @Column(name = "target_kind", length = 32, nullable = false)
    private String targetKind;

    @Column(name = "target_ref", length = 255, nullable = false)
    private String targetRef;

    /** Provider is persisted so retries cannot change routing when configuration changes. */
    @Column(length = 32, nullable = false)
    private String provider = "firebase";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> payload = Map.of();

    @Column(length = 24, nullable = false)
    private String status = "pending";

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 8;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "locked_by", length = 80)
    private String lockedBy;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "last_error_code", length = 120)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;
}
