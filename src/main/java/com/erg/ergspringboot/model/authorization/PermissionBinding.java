package com.erg.ergspringboot.model.authorization;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
        name = "permission_bindings",
        indexes = {
                @Index(name = "idx_permission_bindings_permission", columnList = "permission_id, enforced")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class PermissionBinding extends BaseEntity {

    @Column(name = "permission_id", nullable = false, length = 36)
    private String permissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    private Permission permission;

    @Column(name = "surface_type", nullable = false, length = 32)
    private String surfaceType;

    @Column(name = "surface_identifier", nullable = false, length = 512)
    private String surfaceIdentifier;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Boolean enforced = true;
}
