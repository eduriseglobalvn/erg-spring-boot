package com.erg.ergspringboot.users;

import java.util.Collection;

/** Records an authorization change for users (authz version bump + audit); implemented by the authorization module. */
public interface RoleAssignmentAudit {

    void authorizationChanged(Collection<UserRef> users, String roleId, String reason, UserAuditContext context);

    record UserRef(String userId, String tenantId) {
    }
}
