package com.erg.ergspringboot.platform.graphql;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;

public record GraphQlRequestContext(
        ErgPrincipal principal,
        UserAccessContext access,
        String userId,
        String tenantId,
        String requestedTenantId,
        String requestedPortal,
        String requestId,
        String operationName,
        boolean authorizationPresent,
        List<String> roles,
        List<String> permissions,
        List<String> deniedPermissions,
        List<String> portals
) {

    public static final String CONTEXT_KEY = "ergContext";
}
