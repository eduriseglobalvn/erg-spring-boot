package com.erg.ergspringboot.model.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.model.users.User;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(
        name = "user_sessions",
        indexes = {
                @Index(name = "idx_user_sessions_session_tenant", columnList = "session_id, tenant_id"),
                @Index(name = "idx_user_sessions_access_token_tenant", columnList = "access_token_hash, tenant_id"),
                @Index(name = "idx_user_sessions_refresh_token_tenant", columnList = "refresh_token_hash, tenant_id"),
                @Index(name = "idx_user_sessions_user_tenant_active", columnList = "user_id, tenant_id, revoked_at"),
                @Index(name = "idx_user_sessions_user_tenant_device_active", columnList = "user_id, tenant_id, device_fingerprint, device_id, revoked_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AuthSession extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    private User user;

    private String sessionId;

    private String deviceId;

    private String deviceName;

    @Column(name = "platform", length = 32)
    private String platform;

    private String deviceFingerprint;

    private String portal;

    private String ipAddress;

    private String userAgent;

    private String refreshTokenHash;
    private String previousRefreshTokenHash;
    private OffsetDateTime refreshTokenRotatedAt;

    private String accessTokenHash;

    private String tenantId;

    private OffsetDateTime lastActiveAt;

    private OffsetDateTime expiresAt;

    private OffsetDateTime revokedAt;

    private String revokedReason;
}
