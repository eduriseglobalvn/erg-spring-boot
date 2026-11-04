package com.erg.ergspringboot.users;

import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.AssignScopedRoleRequest;
import com.erg.ergspringboot.common.dto.authorization.AuthorizationDtos.ScopedRoleAssignmentResponse;
import com.erg.ergspringboot.platform.security.ErgPrincipal;

/** Grants a scoped role to a user; implemented by the authorization module. */
public interface ScopedRoleAssigner {

    ScopedRoleAssignmentResponse assign(
            String userId,
            String tenantId,
            AssignScopedRoleRequest request,
            ErgPrincipal actor,
            UserAuditContext auditContext);
}
