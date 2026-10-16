package com.erg.ergspringboot.users;

import java.util.List;

/** Immutable authorization read snapshot owned by the users/account boundary. */
public record UserAuthorizationProjection(
        String userId,
        String tenantId,
        List<String> roleNames,
        List<String> rolePermissions,
        List<String> grantedPermissions,
        List<String> deniedPermissions) {

    public UserAuthorizationProjection {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        roleNames = List.copyOf(roleNames == null ? List.of() : roleNames);
        rolePermissions = List.copyOf(rolePermissions == null ? List.of() : rolePermissions);
        grantedPermissions = List.copyOf(grantedPermissions == null ? List.of() : grantedPermissions);
        deniedPermissions = List.copyOf(deniedPermissions == null ? List.of() : deniedPermissions);
    }

    public List<String> effectivePermissions() {
        return java.util.stream.Stream.concat(rolePermissions.stream(), grantedPermissions.stream())
                .filter(permission -> deniedPermissions.stream()
                        .noneMatch(denied -> denied.equalsIgnoreCase(permission)))
                .distinct()
                .toList();
    }
}
