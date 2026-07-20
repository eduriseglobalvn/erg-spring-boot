package com.erg.ergspringboot.model.authorization;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Authorization permission mapped to the permissions table.
 */
@Entity
@Table(name = "permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Permission extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "group_name")
    private String groupName;

    private String label;

    private String description;

    private String resource;

    private String action;

    private String portal;

    @Column(name = "supported_scope")
    private String supportedScope;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "deprecated_at")
    private java.time.OffsetDateTime deprecatedAt;

    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Role> roles = new LinkedHashSet<>();

    @OneToMany(mappedBy = "permissionRef", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<UserPermissionOverride> userOverrides = new LinkedHashSet<>();

    @OneToMany(mappedBy = "permission", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<PermissionBinding> bindings = new LinkedHashSet<>();
}
