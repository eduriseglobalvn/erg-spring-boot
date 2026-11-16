package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserPermissionOverride;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.User;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The single definition of a user's effective permissions, shared by login, bearer-session
 * principals and the OIDC bridge.
 *
 * <p>Grants come only from directly assigned roles and active scoped role assignments. Per-user permission
 * overrides are retired: the {@code overrides} argument is accepted for source compatibility and ignored.
 * Names are trimmed and lower-cased; results are sorted.</p>
 */
public final class EffectivePermissionResolver {

    private EffectivePermissionResolver() {
    }

    /** Role names from the legacy {@code user_roles} set plus the active scoped assignments, lower-cased. */
    public static List<String> roleNames(User user, Collection<UserRoleAssignment> activeAssignments) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) {
            names.add("user");
        } else {
            user.getRoles().stream()
                    .map(Role::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .forEach(names::add);
        }
        activeAssignments.stream().map(UserRoleAssignment::getRole).filter(Objects::nonNull)
                .map(Role::getName).filter(name -> name != null && !name.isBlank())
                .map(String::trim).map(name -> name.toLowerCase(Locale.ROOT)).forEach(names::add);
        return List.copyOf(names);
    }

    public static Result resolve(User user, Collection<UserRoleAssignment> activeAssignments, OffsetDateTime now) {
        if (user == null) {
            return new Result(List.of(), List.of());
        }
        return resolve(user.getRoles(), null, activeAssignments, now);
    }

    public static Result resolve(
            Collection<Role> roles,
            Collection<UserPermissionOverride> overrides,
            Collection<UserRoleAssignment> activeAssignments,
            OffsetDateTime now
    ) {
        Set<String> granted = new TreeSet<>();
        Stream<Role> assignedRoles = activeAssignments == null ? Stream.empty()
                : activeAssignments.stream().filter(Objects::nonNull).map(UserRoleAssignment::getRole);
        Stream.concat(roles == null ? Stream.empty() : roles.stream(), assignedRoles)
                .filter(Objects::nonNull)
                .flatMap(role -> role.getPermissions() == null ? Stream.empty() : role.getPermissions().stream())
                .filter(Objects::nonNull)
                .map(Permission::getName)
                .map(EffectivePermissionResolver::normalize)
                .filter(Objects::nonNull)
                .forEach(granted::add);
        return new Result(List.copyOf(granted), List.of());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Result(List<String> granted, List<String> denied) {
    }
}
