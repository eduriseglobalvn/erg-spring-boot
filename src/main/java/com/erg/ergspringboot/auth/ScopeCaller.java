package com.erg.ergspringboot.auth;

import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import java.util.List;

/**
 * Who is asking {@link SessionUnitScopeResolver}. {@code platformWide} (system.super_admin holding {@code *}) alone may
 * list units across tenants; {@code teacherId} non-null means a plain teacher whose classes are only the ones they teach.
 */
record ScopeCaller(
        User user,
        List<String> roles,
        List<UserRoleAssignment> assignments,
        boolean canAccessGlobalErg,
        boolean platformWide,
        boolean allUnits,
        String tenantId,
        String teacherId
) {
    /** Every unit of one tenant: the case where a repository page or a targeted lookup replaces the full list. */
    boolean tenantWide() {
        return allUnits && !platformWide;
    }
}
