package com.erg.ergspringboot.platform.security;

import java.util.List;
import java.util.Locale;

/**
 * Fail-closed capability and scope policy for a verified principal.
 *
 * <p>Route/domain aliases are accepted only as a lookup key for the required
 * canonical workspace. The principal's legacy {@code portals} field and
 * request headers are never used as a grant.</p>
 */
public final class AuthorizationCapabilityPolicy {

    public boolean hasWorkspace(ErgPrincipal principal, String requestedPortal) {
        if (principal == null) {
            return false;
        }
        if (requestedPortal == null || requestedPortal.isBlank()) {
            return true;
        }
        return WorkspaceCapabilityMapper.fromLegacyPortal(requestedPortal)
                .map(capability -> WorkspaceCapabilityMapper
                        .canonicalWorkspacesFromVerifiedGrants(
                                principal.roles(), principal.permissions(), principal.deniedPermissions())
                        .contains(capability.name()))
                .orElse(false);
    }

    public boolean tenantMatches(ErgPrincipal principal, String requestedTenant) {
        if (principal == null || principal.tenantId() == null || principal.tenantId().isBlank()) {
            return false;
        }
        return requestedTenant == null || requestedTenant.isBlank()
                || principal.tenantId().equals(requestedTenant.trim());
    }

    public boolean canAccessScope(ErgPrincipal principal, String scopeType, String scopeId) {
        if (principal == null || !tenantMatches(principal, principal.tenantId())) {
            return false;
        }
        if (isPlatformWide(principal)) {
            return true;
        }
        return AccessScopeAssembler.from(principal).canAccess(scopeType, scopeId);
    }

    /** A wildcard is privileged only when the verified system role is present. */
    public boolean isPlatformWide(ErgPrincipal principal) {
        if (principal == null || principal.roles() == null || principal.permissions() == null) {
            return false;
        }
        return WorkspaceCapabilityMapper.isPlatformWide(
                principal.roles(), principal.permissions(), principal.deniedPermissions());
    }

    public boolean isPermissionWildcardAllowed(ErgPrincipal principal, String grant) {
        return grant != null && "*".equals(normalize(grant)) && isPlatformWide(principal);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
