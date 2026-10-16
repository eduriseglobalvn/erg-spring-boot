package com.erg.ergspringboot.service.elearning.readmodel;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import java.util.Map;

public record StudentDashboardReadScope(
        String userId,
        String tenantId,
        String requestedTenantId,
        String requestedStudentUserId,
        List<String> roles,
        List<String> permissions,
        List<String> deniedPermissions,
        List<String> portals,
        String accountType,
        String accessLevel,
        String requestedClassId
) {

    public StudentDashboardReadScope(
            String userId,
            String tenantId,
            String requestedTenantId,
            String requestedStudentUserId,
            List<String> roles,
            List<String> permissions,
            List<String> deniedPermissions,
            List<String> portals,
            String accountType,
            String accessLevel
    ) {
        this(
                userId, tenantId, requestedTenantId, requestedStudentUserId, roles, permissions,
                deniedPermissions, portals, accountType, accessLevel, null
        );
    }

    public ErgPrincipal toPrincipal() {
        return ErgPrincipal.authenticated(
                userId,
                "graphql-student-dashboard",
                tenantId,
                roles == null ? List.of() : List.copyOf(roles),
                permissions == null ? List.of() : List.copyOf(permissions),
                deniedPermissions == null ? List.of() : List.copyOf(deniedPermissions),
                portals == null ? List.of() : List.copyOf(portals),
                accountType,
                accessLevel,
                Map.of()
        );
    }
}
