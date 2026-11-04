package com.erg.ergspringboot.enterpriseconnectors.internal;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Tenant and permission checks for enterprise connector commands and queries. */
final class EnterpriseConnectorAccessGuard {

    private EnterpriseConnectorAccessGuard() {
    }

    static String tenant(UserAccessContext access) {
        if (access == null || access.userId() == null || access.userId().isBlank()) {
            throw new ForbiddenException("authenticated access context is required");
        }
        return AuthenticatedTenant.require(access.tenantId());
    }

    static void ensureWrite() {
        if (!permission("enterprise.connectors.manage")) throw new ForbiddenException("connector management is required");
    }

    static void ensureRead() {
        if (!permission("enterprise.connectors.read") && !permission("enterprise.connectors.manage")) {
            throw new ForbiddenException("connector read permission is required");
        }
    }

    static void ensureSync() {
        if (!permission("enterprise.connectors.sync") && !permission("enterprise.connectors.manage")) {
            throw new ForbiddenException("connector sync permission is required");
        }
    }

    private static boolean permission(String expected) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ErgPrincipal principal)) return false;
        if (principal.deniedPermissions().stream().map(EnterpriseConnectorAccessGuard::lower)
                .anyMatch(value -> value.equals("*") || value.equals(expected))) return false;
        return principal.permissions().stream().map(EnterpriseConnectorAccessGuard::lower)
                .anyMatch(value -> value.equals("*") || value.equals(expected));
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
