package com.erg.ergspringboot.model.audit;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/audit/domain/entity/audit_log.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    private String id;

    private String userId;

    private String userEmail;

    private String action;

    private String resourceType;

    private String resourceId;

    private String changes;

    private String metadata;

    private String ipAddress;

    private String userAgent;

    private String tenantId;

    private OffsetDateTime timestamp;
}
