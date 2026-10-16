package com.erg.ergspringboot.platform.security;

/** Fail-closed normalization for tenant identity already derived from verified authentication. */
public final class AuthenticatedTenant {

    private AuthenticatedTenant() {
    }

    public static String require(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new AuthenticationRequiredException();
        return tenantId.trim();
    }
}
