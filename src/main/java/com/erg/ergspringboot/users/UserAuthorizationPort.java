package com.erg.ergspringboot.users;

import java.util.List;
import java.util.Optional;

/** Cross-module read port for tenant-scoped authorization state. */
public interface UserAuthorizationPort {

    Optional<UserAuthorizationProjection> find(String userId, String tenantId);

    List<UserPermissionOverrideProjection> findOverrides(String userId, String tenantId);
}
