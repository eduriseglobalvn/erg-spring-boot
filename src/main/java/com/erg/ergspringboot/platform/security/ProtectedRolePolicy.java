package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Role-grant rule: nobody grants a role carrying a permission they do not hold themselves. Only a
 * platform-wide actor (system role plus wildcard) holds {@code *}, so only it can grant the wildcard role.
 * Per-account rules (self, last admin, scope) live in {@link AccountActionGuard}.
 */
public final class ProtectedRolePolicy {

    private final AuthorizationCapabilityPolicy capabilityPolicy = new AuthorizationCapabilityPolicy();

    public void assertRoleDefinitionAllowed(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new BadRequestException("role name is required");
        }
    }

    public void assertCanGrant(ErgPrincipal actor, Collection<Role> roles) {
        if (actor == null) {
            throw new ForbiddenException(AccountActionGuard.DENIED_MESSAGE);
        }
        if (roles == null || capabilityPolicy.isPlatformWide(actor)) {
            return;
        }
        Set<String> have = actor.permissions().stream().map(this::normalize).collect(Collectors.toSet());
        boolean beyondActor = roles.stream().filter(Objects::nonNull)
                .flatMap(role -> role.getPermissions() == null ? Stream.<Permission>empty() : role.getPermissions().stream())
                .map(Permission::getName).filter(Objects::nonNull).map(this::normalize)
                .anyMatch(permission -> "*".equals(permission) || !have.contains(permission));
        if (beyondActor) {
            throw new ForbiddenException(AccountActionGuard.DENIED_MESSAGE);
        }
    }

    /** Roles and their permission sets are global, so only platform-wide actors may define them. */
    public void assertCanManageRoleDefinitions(ErgPrincipal actor) {
        if (!capabilityPolicy.isPlatformWide(actor)) {
            throw new ForbiddenException(AccountActionGuard.DENIED_MESSAGE);
        }
    }

    public void assertCanManageDefinition(ErgPrincipal actor, Role role) {
        assertCanManageRoleDefinitions(actor);
    }

    /** Kept only while the permission-override use cases still compile; the overrides are being removed. */
    public void assertCanGrantPermissions(ErgPrincipal actor, String targetUserId, Collection<String> permissionNames) {
        if (actor == null || actor.userId() == null || actor.userId().isBlank()) {
            throw new ForbiddenException(AccountActionGuard.DENIED_MESSAGE);
        }
        if (targetUserId != null && actor.userId().equals(targetUserId.trim())) {
            throw new ForbiddenException(AccountActionGuard.DENIED_MESSAGE);
        }
        if (permissionNames != null && permissionNames.stream().map(this::normalize)
                .anyMatch(p -> p.startsWith("*") || p.startsWith("rbac."))) {
            assertCanManageRoleDefinitions(actor);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
