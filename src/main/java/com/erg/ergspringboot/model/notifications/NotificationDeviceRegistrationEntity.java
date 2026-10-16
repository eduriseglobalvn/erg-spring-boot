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
        name = "notification_device_registrations",
        indexes = {
                @Index(name = "idx_notification_devices_user_active", columnList = "tenant_id, user_id, portal, enabled, last_seen_at"),
                @Index(name = "idx_notification_devices_stale", columnList = "enabled, last_seen_at, token_updated_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NotificationDeviceRegistrationEntity extends BaseEntity {

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(length = 32, nullable = false)
    private String portal;

    @Column(length = 40, nullable = false)
    private String platform;

    @Column(name = "firebase_installation_id", length = 180)
    private String firebaseInstallationId;

    @Column(name = "registration_token", columnDefinition = "text")
    private String registrationToken;

    @Column(name = "registration_token_hash", length = 128)
    private String registrationTokenHash;

    @Column(name = "endpoint_hash", length = 128)
    private String endpointHash;

    @Column(name = "service_worker_scope", length = 255)
    private String serviceWorkerScope;

    @Column(name = "browser_name", length = 80)
    private String browserName;

    @Column(name = "browser_version", length = 80)
    private String browserVersion;

    @Column(name = "os_name", length = 80)
    private String osName;

    @Column(name = "user_agent_hash", length = 128)
    private String userAgentHash;

    @Column(length = 16)
    private String locale;

    @Column(length = 64)
    private String timezone;

    @Column(name = "permission_state", length = 24, nullable = false)
    private String permissionState = "default";

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "token_updated_at")
    private OffsetDateTime tokenUpdatedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;
}
