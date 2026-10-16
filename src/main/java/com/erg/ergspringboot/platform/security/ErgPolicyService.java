package com.erg.ergspringboot.platform.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ErgPolicyService {

    private final AuthorizationCapabilityPolicy capabilityPolicy = new AuthorizationCapabilityPolicy();

    private static final Map<String, List<String>> ALIASES = Map.of(
            "roles.read", List.of("rbac.role.read", "rbac.permission.read"),
            "roles.create", List.of("rbac.role.create"),
            "roles.update", List.of("rbac.role.update"),
            "roles.delete", List.of("rbac.role.delete"),
            "roles.assign", List.of("rbac.role.assign", "rbac.override.manage"),
            "content.manage", List.of("content.*", "curriculum.*", "cms.*", "courses.*", "media.*"),
            "system.logs", List.of("audit.read")
    );
    // Baseline permissions every authenticated principal holds. Object-level ownership for
    // these (e.g. "own notifications", "own session") is enforced in the service layer via
    // the verified principal, not by withholding the permission itself.
    private static final Set<String> BASELINE_PERMISSIONS = Set.of("profile.self");

    public Map<String, Object> decide(ErgPrincipal principal, String permission, String portal, String tenantId) {
        boolean portalAllowed = capabilityPolicy.hasWorkspace(principal, portal);
        boolean tenantAllowed = capabilityPolicy.tenantMatches(principal, tenantId);
        boolean denied = matchesAny(expand(principal.deniedPermissions()), permission);
        boolean granted = !denied && (BASELINE_PERMISSIONS.contains(permission)
                || matchesAny(principal, expand(grants(principal)), permission));
        boolean allowed = portalAllowed && tenantAllowed && granted;
        return Map.of(
                "allowed", allowed,
                "permission", permission,
                "portal", portal == null ? "" : portal,
                "tenantId", tenantId == null ? principal.tenantId() : tenantId,
                "denyOverride", denied,
                "portalAllowed", portalAllowed,
                "tenantAllowed", tenantAllowed
        );
    }

    public boolean isAllowed(ErgPrincipal principal, String permission, String portal, String tenantId) {
        Object allowed = decide(principal, permission, portal, tenantId).get("allowed");
        return Boolean.TRUE.equals(allowed);
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
        List<String> denied = principal == null || principal.deniedPermissions() == null
                ? List.of()
                : expand(principal.deniedPermissions());
        if (permissions.stream().anyMatch(permission -> matchesAny(denied, permission))) {
            return false;
        }
        return permissions.stream().anyMatch(permission -> isAllowed(principal, permission, portal, tenantId));
    }

    public Map<String, Object> matrix() {
        return Map.of(
                "denyOverrideWins", true,
                "wildcards", List.of("*", "lms.*", "cms.*", "content.*", "curriculum.*", "media.*", "rbac.*", "*.read"),
                "aliases", ALIASES,
                "lmsScopeEnforcedInServiceLayer", true,
                "springSecurityModel", "Spring Security filter-chain route authorization"
        );
    }

    private boolean matchesAny(ErgPrincipal principal, List<String> permissions, String required) {
        return permissions.stream().anyMatch(permission ->
                capabilityPolicy.isPermissionWildcardAllowed(principal, permission)
                        || matches(permission, required));
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

    private List<String> expand(List<String> permissions) {
        return permissions.stream()
                .flatMap(permission -> expanded(permission, 0).stream())
                .distinct()
                .toList();
    }

    private List<String> expanded(String permission, int depth) {
        if (depth > 5) {
            return List.of(permission);
        }
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(permission),
                        ALIASES.getOrDefault(permission, List.of()).stream()
                                .flatMap(item -> expanded(item, depth + 1).stream())
                )
                .distinct()
                .toList();
    }

    private List<String> grants(ErgPrincipal principal) {
        return principal.permissions() == null ? List.of() : principal.permissions();
    }
}
