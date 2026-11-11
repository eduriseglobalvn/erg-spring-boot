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
        List<String> held = WorkspaceCapabilityMapper.canonicalWorkspacesFromVerifiedGrants(
                principal.roles(), principal.permissions(), principal.deniedPermissions());
        return WorkspaceCapabilityMapper.fromLegacyPortal(requestedPortal)
                .map(capability -> held.contains(capability.name())
                        || (capability == WorkspaceCapability.TEACHER && held.contains(WorkspaceCapability.STUDENT.name())))
                .orElse(false);
    }
    // Why STUDENT passes for the lms portal: a learner's own assignments, quiz packages and attempts are served
    // under /api/v1/lms, and a learner holds only the STUDENT workspace. This check only opens the API family;
    // what a learner may call inside it is decided by the permission on each endpoint (lms.*_self, lms.quiz.play).

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
