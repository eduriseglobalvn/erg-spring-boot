package com.erg.ergspringboot.model.notifications;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "notification_delivery_attempts",
        indexes = {
                @Index(name = "idx_notification_delivery_notification", columnList = "notification_id, created_at"),
                @Index(name = "idx_notification_delivery_status", columnList = "tenant_id, status, created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotificationDeliveryAttemptEntity extends BaseEntity {

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "outbox_id", length = 36)
    private String outboxId;

    @Column(name = "notification_id", length = 36, nullable = false)
    private String notificationId;

    @Column(name = "device_registration_id", length = 36)
    private String deviceRegistrationId;

    @Column(name = "target_kind", length = 32, nullable = false)
    private String targetKind;

    @Column(name = "target_ref_hash", length = 128)
    private String targetRefHash;

    @Column(length = 32, nullable = false)
    private String provider = "firebase";

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "firebase_error_code", length = 120)
    private String firebaseErrorCode;

    @Column(length = 32, nullable = false)
    private String status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "response_sample", length = 500)
    private String responseSample;
}
