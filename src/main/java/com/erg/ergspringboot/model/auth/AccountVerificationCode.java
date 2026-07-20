package com.erg.ergspringboot.model.auth;

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

/**
 * One-time verification codes for recovery-email and first-login password challenges.
 * Codes are stored hashed only; plaintext is never persisted or logged.
 */
@Entity
@Table(
        name = "account_verification_codes",
        indexes = {
                @Index(name = "idx_account_verification_codes_user_purpose", columnList = "user_id, purpose, expires_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AccountVerificationCode extends BaseEntity {

    @Column(name = "user_id", length = 24, nullable = false)
    private String userId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "purpose", length = 32, nullable = false)
    private String purpose;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "target_email")
    private String targetEmail;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "last_sent_at", nullable = false)
    private OffsetDateTime lastSentAt;
}
