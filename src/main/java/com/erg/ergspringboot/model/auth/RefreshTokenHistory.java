package com.erg.ergspringboot.model.auth;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_token_history", indexes = {
        @Index(name = "idx_refresh_token_history_hash", columnList = "token_hash"),
        @Index(name = "idx_refresh_token_history_session", columnList = "session_id")
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RefreshTokenHistory extends BaseEntity {
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;
    @Column(name = "consumed_at", nullable = false)
    private OffsetDateTime consumedAt;
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}
