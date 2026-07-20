package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.authorization.Role;
import java.util.Collection;
import java.util.Locale;

/**
 * Keeps reserved/system role semantics out of request-bound services.
 * Ordinary role names are data; system roles are protected control-plane identities.
 */
public final class ProtectedRolePolicy {

    private static final String SYSTEM_PREFIX = "system.";

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

    private void assertPlatformWildcard(ErgPrincipal actor, String operation) {
        if (actor == null) {
            throw new ForbiddenException(operation + " requires authenticated platform wildcard authority");
        }
        boolean wildcard = actor.permissions() != null && actor.permissions().stream()
                .map(this::normalize)
                .anyMatch("*"::equals);
        if (wildcard) {
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
