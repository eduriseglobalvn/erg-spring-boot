package com.erg.ergspringboot.model.auth;

import com.erg.ergspringboot.model.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(
        name = "auth_login_attempts",
        indexes = {
                @Index(name = "idx_auth_login_attempts_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_auth_login_attempts_email_created", columnList = "attempted_email, created_at"),
                @Index(name = "idx_auth_login_attempts_ip_result", columnList = "ip_address, result")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthLoginAttempt {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    private String tenantId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;

    private String attemptedEmail;

    private String attemptedEmailHash;

    private String ipAddress;

    private String countryCode;

    private String countryName;

    private String continentCode;

    private String userAgent;

    private String deviceId;

    private String deviceName;

    private String portal;

    private String result;

    private String reason;

    private OffsetDateTime createdAt;
}
