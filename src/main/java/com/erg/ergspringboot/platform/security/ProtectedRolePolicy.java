package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import java.util.Collection;
import java.util.Locale;

/**
 * Keeps reserved/system role semantics out of request-bound services.
 * Ordinary role names are data; system roles are protected control-plane identities.
 */
public final class ProtectedRolePolicy {

    private static final String SYSTEM_PREFIX = "system.";
    private final AuthorizationCapabilityPolicy capabilityPolicy = new AuthorizationCapabilityPolicy();

    public void assertRoleDefinitionAllowed(String roleName) {
        String normalized = normalize(roleName);
        if (normalized == null) {
            throw new BadRequestException("role name is required");
        }
        if (normalized.startsWith(SYSTEM_PREFIX)) {
            throw new BadRequestException("system role names are reserved");
        }
    }

    public void assertCanGrant(ErgPrincipal actor, Collection<Role> roles) {
        boolean containsGodMode = roles != null && roles.stream()
                .map(Role::getName)
                .map(this::normalize)
                .anyMatch("system.god_mode"::equals);
        if (containsGodMode) {
            throw new ForbiddenException("system.god_mode requires step-up authentication and is disabled for ordinary assignment");
        }
        boolean containsProtectedRole = roles != null && roles.stream().anyMatch(this::isProtectedRole);
        if (!containsProtectedRole) {
            return;
        }
        assertPlatformWildcard(actor, "protected role assignment");
    }

    public void assertCanManageDefinition(ErgPrincipal actor, Role role) {
        if (!isProtectedRole(role)) {
            return;
        }
        assertPlatformWildcard(actor, "protected role modification");
    }

    /**
     * Direct permission overrides use the same grant policy as role assignment: no self-grant,
     * and wildcard/RBAC control-plane permissions only from a platform-wide actor.
     */
    public void assertCanGrantPermissions(ErgPrincipal actor, String targetUserId, Collection<String> permissionNames) {
        if (actor == null || actor.userId() == null || actor.userId().isBlank()) {
            throw new ForbiddenException("permission assignment requires an authenticated actor");
        }
        if (targetUserId != null && actor.userId().equals(targetUserId.trim())) {
            throw new ForbiddenException("permission self-assignment is not allowed");
        }
        boolean privileged = permissionNames != null && permissionNames.stream().anyMatch(this::isPrivilegedPermission);
        if (privileged) {
            assertPlatformWildcard(actor, "wildcard or RBAC permission assignment");
        }
    }

    /** Roles and their permission sets are global, so only platform-wide actors may define them. */
    public void assertCanManageRoleDefinitions(ErgPrincipal actor) {
        assertPlatformWildcard(actor, "role definition management");
    }

    public boolean isPrivilegedPermission(String permissionName) {
        String normalized = normalize(permissionName);
        return normalized != null && (normalized.startsWith("*") || normalized.startsWith("rbac."));
    }

    private void assertPlatformWildcard(ErgPrincipal actor, String operation) {
        if (actor == null) {
            throw new ForbiddenException(operation + " requires authenticated platform wildcard authority");
        }
        if (capabilityPolicy.isPlatformWide(actor)) {
            return;
        }
        throw new ForbiddenException(operation + " requires platform wildcard authority");
    }

    public boolean isProtectedRole(Role role) {
        return role != null && isProtectedRole(role.getName());
    }

    public boolean isProtectedRole(String roleName) {
        String normalized = normalize(roleName);
        return normalized != null && normalized.startsWith(SYSTEM_PREFIX);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
