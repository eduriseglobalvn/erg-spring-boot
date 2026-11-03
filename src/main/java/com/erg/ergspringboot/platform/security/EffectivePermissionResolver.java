package com.erg.ergspringboot.platform.security;

import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.users.User;
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
 * <ol>
 *   <li>Grants come from directly assigned roles and from active scoped role assignments.</li>
 *   <li>Unexpired direct overrides are applied last: {@code grant}/{@code allow} (or blank) add
 *       the permission; every other grant type ({@code deny}, {@code revoke}, unknown) denies it.
 *       An explicit deny therefore always wins over a role grant.</li>
 *   <li>Names are trimmed and lower-cased; results are sorted.</li>
 * </ol>
 */
public final class EffectivePermissionResolver {

    private EffectivePermissionResolver() {
    }

    public static Result resolve(User user, Collection<UserRoleAssignment> activeAssignments, OffsetDateTime now) {
        if (user == null) {
            return new Result(List.of(), List.of());
        }
        return resolve(user.getRoles(), user.getPermissionOverrides(), activeAssignments, now);
    }

    public static Result resolve(
            Collection<Role> roles,
            Collection<UserPermissionOverride> overrides,
            Collection<UserRoleAssignment> activeAssignments,
            OffsetDateTime now
    ) {
        Set<String> granted = new TreeSet<>();
        Set<String> denied = new TreeSet<>();
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
        if (overrides != null) {
            for (UserPermissionOverride override : overrides) {
                String permission = override == null ? null : normalize(override.getPermission());
                if (permission == null || isExpired(override, now)) {
                    continue;
                }
                if (isGrant(override.getGrantType())) {
                    granted.add(permission);
                    denied.remove(permission);
                } else {
                    denied.add(permission);
                    granted.remove(permission);
                }
            }
        }
        return new Result(List.copyOf(granted), List.copyOf(denied));
    }

    public static boolean isGrant(String grantType) {
        return grantType == null || grantType.isBlank()
                || "grant".equalsIgnoreCase(grantType.trim())
                || "allow".equalsIgnoreCase(grantType.trim());
    }

    private static boolean isExpired(UserPermissionOverride override, OffsetDateTime now) {
        return override.getExpiresAt() != null && now != null && !override.getExpiresAt().isAfter(now);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Result(List<String> granted, List<String> denied) {
    }
}
