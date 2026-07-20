package com.erg.ergspringboot.model.authorization;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_role_assignments")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserRoleAssignment extends BaseEntity {
    @Column(name = "user_id", nullable = false, length = 24)
    private String userId;

    @Column(name = "role_id", nullable = false, length = 24)
    private String roleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Role role;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType;

    @Column(name = "scope_id", length = 36)
    private String scopeId;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "assigned_by", nullable = false, length = 24)
    private String assignedBy;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by", length = 24)
    private String revokedBy;

    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;
}
