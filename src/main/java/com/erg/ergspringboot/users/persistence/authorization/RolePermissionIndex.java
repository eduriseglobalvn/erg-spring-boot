package com.erg.ergspringboot.users.persistence.authorization;

import java.util.List;
import java.util.Objects;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every role with its permission names, as immutable values. The staff list and the assignable-roles check only
 * classify roles by permission, so they share this cached read instead of hydrating all roles x permissions per call.
 * Cleared with the rest of {@code authorizationRoles} by the catalog cache evictor on any role/permission change.
 */
@Component
public class RolePermissionIndex {

    public record Entry(String id, String name, List<String> permissionNames) {
    }

    private final RoleRepository roles;

    public RolePermissionIndex(RoleRepository roles) {
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "authorizationRoles", key = "'rolePermissions'")
    public List<Entry> entries() {
        return roles.findAllWithPermissionsAndUsers().stream()
                .map(role -> new Entry(role.getId(), role.getName(), role.getPermissions() == null ? List.of()
                        : role.getPermissions().stream().map(Permission::getName).filter(Objects::nonNull).toList()))
                .toList();
    }
}
