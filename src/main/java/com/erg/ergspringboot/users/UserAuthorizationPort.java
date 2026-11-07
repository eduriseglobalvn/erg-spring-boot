package com.erg.ergspringboot.users;

import java.util.List;
import java.util.Optional;

/** Cross-module read port for tenant-scoped authorization state. */
public interface UserAuthorizationPort {

    Optional<UserAuthorizationProjection> find(String userId, String tenantId);

    List<UserPermissionOverrideProjection> findOverrides(String userId, String tenantId);

    /**
     * What a bearer session for this user would carry now, built like ErgPrincipalService: role names from direct
     * roles plus active (not revoked, not expired) role assignments, lower-cased; permissions from
     * EffectivePermissionResolver ({@code rolePermissions} = granted, {@code deniedPermissions} = denied, no separate
     * user grants). Empty when the user is not in the tenant.
     */
    Optional<UserAuthorizationProjection> findEffective(String userId, String tenantId);

    /**
     * Education unit ids the user is attached to in {@code tenantId} (LMS access scopes and active unit-scoped role
     * assignments) plus every descendant unit: the reach of a unit-scoped admin, as session bootstrap computes it.
     * Empty when the user is not in the tenant.
     */
    List<String> managedEducationUnitIds(String userId, String tenantId);
}
