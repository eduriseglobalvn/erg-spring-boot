package com.erg.ergspringboot.model.sessions;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/sessions/domain/entity/session.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    private String id;

    private String tenantId;

    private String userId;

    private String sessionId;

    private String ipAddress;

    private String userAgent;

    private OffsetDateTime createdAt;

    private OffsetDateTime lastActive;

    private OffsetDateTime expiresAt;

    private Boolean isActive;

    private OffsetDateTime revokedAt;
}
