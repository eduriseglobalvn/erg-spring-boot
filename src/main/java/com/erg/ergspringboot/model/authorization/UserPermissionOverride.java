package com.erg.ergspringboot.model.authorization;

import com.erg.ergspringboot.model.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Per-user permission grant/deny override.
 */
@Entity
@Table(name = "user_permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionOverride {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    private User user;

    @Column(name = "permission", nullable = false)
    private String permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission", referencedColumnName = "name", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    private Permission permissionRef;

    private String grantType;

    private String reason;

    private OffsetDateTime expiresAt;

    private String createdBy;

    private OffsetDateTime createdAt;
}
