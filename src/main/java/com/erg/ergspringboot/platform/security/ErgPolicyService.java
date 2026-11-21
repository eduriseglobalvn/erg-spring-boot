package com.erg.ergspringboot.platform.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ErgPolicyService {

    private final AuthorizationCapabilityPolicy capabilityPolicy = new AuthorizationCapabilityPolicy();

    // Baseline permissions every authenticated principal holds. Object-level ownership for
    // these (e.g. "own notifications", "own session") is enforced in the service layer via
    // the verified principal, not by withholding the permission itself.
    private static final Set<String> BASELINE_PERMISSIONS = Set.of("profile.self");

    Map<String, Object> decide(ErgPrincipal principal, String permission, String portal, String tenantId) {
        PrincipalDerived derived = PrincipalDerived.of(principal);
        boolean portalAllowed = capabilityPolicy.hasWorkspace(principal, portal);
        boolean tenantAllowed = capabilityPolicy.tenantMatches(principal, tenantId);
        boolean denied = matchesAny(derived.denies(), permission);
        boolean granted = isGranted(principal, derived, permission, denied);
        return Map.of(
                "allowed", portalAllowed && tenantAllowed && granted,
                "permission", permission,
                "portal", portal == null ? "" : portal,
                "tenantId", tenantId == null ? principal.tenantId() : tenantId,
                "denyOverride", denied,
                "portalAllowed", portalAllowed,
                "tenantAllowed", tenantAllowed
        );
    }

    public boolean isAllowed(ErgPrincipal principal, String permission, String portal, String tenantId) {
        if (principal == null || !capabilityPolicy.tenantMatches(principal, tenantId)
                || !capabilityPolicy.hasWorkspace(principal, portal)) {
            return false;
        }
        PrincipalDerived derived = PrincipalDerived.of(principal);
        return isGranted(principal, derived, permission, matchesAny(derived.denies(), permission));
    }

    private boolean isGranted(ErgPrincipal principal, PrincipalDerived derived, String permission, boolean denied) {
        return !denied && (BASELINE_PERMISSIONS.contains(permission) || matchesAny(principal, derived.grants(), permission));
    }

    /**
     * Evaluates an alternative permission set as one policy decision. An explicit deny
     * for any requested alternative wins before a broad grant can satisfy another one.
     */
    public boolean isAllowedAny(
            ErgPrincipal principal,
            String portal,
            String tenantId,
            List<String> permissions
    ) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        if (principal == null) {
            return false;
        }
        List<String> denied = PrincipalDerived.of(principal).denies();
        if (permissions.stream().anyMatch(permission -> matchesAny(denied, permission))) {
            return false;
        }
        return permissions.stream().anyMatch(permission -> isAllowed(principal, permission, portal, tenantId));
    }

    public Map<String, Object> matrix() {
        return Map.of(
                "denyOverrideWins", true,
                "wildcards", List.of("*", "lms.*", "cms.*", "content.*", "curriculum.*", "media.*", "rbac.*", "*.read"),
                "aliases", PrincipalDerived.ALIASES,
                "lmsScopeEnforcedInServiceLayer", true,
                "springSecurityModel", "Spring Security filter-chain route authorization"
        );
    }

    /** A bare {@code *} grant is universal only for a platform-wide principal; denies keep full matching. */
    private boolean matchesAny(ErgPrincipal principal, List<String> permissions, String required) {
        return permissions.stream().anyMatch(permission -> "*".equals(permission)
                ? capabilityPolicy.isPermissionWildcardAllowed(principal, permission)
                : matches(permission, required));
    }

    private boolean matchesAny(List<String> permissions, String required) {
        return permissions.stream().anyMatch(permission -> matches(permission, required));
    }

    private boolean matches(String grant, String required) {
        if ("*".equals(grant) || grant.equals(required)) {
            return true;
        }
        if (grant.endsWith(".*")) {
            return required.startsWith(grant.substring(0, grant.length() - 1));
        }
        if (grant.startsWith("*.")) {
            return required.endsWith(grant.substring(1));
        }
        return false;
    }
}
