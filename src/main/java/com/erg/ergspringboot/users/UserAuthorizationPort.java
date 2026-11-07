package com.erg.ergspringboot.users;

import java.util.List;
import java.util.Optional;

/** Cross-module read port for tenant-scoped authorization state. */
public interface UserAuthorizationPort {

    Optional<UserAuthorizationProjection> find(String userId, String tenantId);

    List<UserPermissionOverrideProjection> findOverrides(String userId, String tenantId);

    /**
     * Education unit ids the user is attached to in {@code tenantId} (LMS access scopes and active unit-scoped role
     * assignments) plus every descendant unit: the reach of a unit-scoped admin, as session bootstrap computes it.
     * Empty when the user is not in the tenant.
     */
    List<String> managedEducationUnitIds(String userId, String tenantId);
}
