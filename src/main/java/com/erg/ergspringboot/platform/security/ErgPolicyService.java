package com.erg.ergspringboot.platform.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ErgPolicyService {

    private static final Map<String, List<String>> ALIASES = Map.of(
            "roles.read", List.of("rbac.role.read", "rbac.permission.read"),
            "roles.create", List.of("rbac.role.create"),
            "roles.update", List.of("rbac.role.update"),
            "roles.delete", List.of("rbac.role.delete"),
            "roles.assign", List.of("rbac.role.assign", "rbac.override.manage"),
            "content.manage", List.of("cms.*", "courses.*", "media.*"),
            "system.logs", List.of("audit.read")
    );
    private static final Set<String> SUPER_ADMIN_ROLES = Set.of("system.super_admin", "erg_super_admin", "SUPER_ADMIN", "admin");
    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.ofEntries(
            Map.entry("system.rbac_admin", List.of("rbac.*")),
            Map.entry("system.audit_reader", List.of("audit.read")),
            Map.entry("media.manager", List.of("media.*")),
            Map.entry("hoclieu.admin", List.of("hoclieu.*")),
            Map.entry("hoclieu.editor", List.of("hoclieu.manage", "hoclieu.resource.*", "hoclieu.asset.*", "hoclieu.progress.write")),
            Map.entry("hoclieu.viewer", List.of("hoclieu.resource.read", "hoclieu.asset.launch", "hoclieu.asset.download")),
            Map.entry("lms.admin", List.of("lms.*", "lms.read", "lms.manage")),
            Map.entry("lms.operator", List.of("lms.unit.*", "lms.class.*", "lms.student.*", "lms.scope.*", "lms.dashboard.read")),
            Map.entry("lms.teacher", List.of(
                    "lms.unit.read", "lms.class.read", "lms.student.read",
                    "lms.quiz.*", "lms.assignment.*", "lms.grade.read", "lms.report.read",
                    "lms.discussion.*", "lms.announcement.read", "lms.document.read", "lms.dashboard.read"
            )),
            Map.entry("lms.student", List.of("profile.self", "lms.assignment.read_self", "lms.assignment.submit", "lms.quiz.read", "lms.report.read")),
            Map.entry("seo.staff", List.of("crm.school.create", "crm.school.read", "crm.deal.manage", "crm.contact.manage", "crm.activity.manage")),
            Map.entry("seo.lead", List.of("crm.school.read", "crm.deal.manage", "crm.contact.manage", "crm.activity.manage", "crm.analytics.read")),
            Map.entry("seo.manager", List.of("crm.school.read", "crm.deal.manage", "crm.contact.manage", "crm.activity.manage", "crm.analytics.read")),
            Map.entry("center.manager", List.of("organization.school.activate", "lms.*", "hoclieu.*")),
            Map.entry("school.manager", List.of("lms.*", "hoclieu.resource.read", "hoclieu.progress.write")),
            Map.entry("executive.director", List.of("crm.analytics.read", "lms.read", "audit.read")),
            Map.entry("elearning.admin", List.of("elearning.*")),
            Map.entry("elearning.instructor", List.of("elearning.course.*", "elearning.assignment.*")),
            Map.entry("elearning.learner", List.of("elearning.assignment.read_self", "elearning.assignment.submit")),
            Map.entry("content_manager", List.of("content.manage", "cms.*", "courses.*", "seo.*", "crawler.*", "documents.*")),
            Map.entry("editor", List.of("cms.posts.*", "cms.categories.*", "media.*")),
            Map.entry("cms.editor", List.of("cms.posts.*", "cms.pages.*", "cms.menus.*", "cms.categories.*", "media.*")),
            Map.entry("viewer", List.of("*.read")),
            Map.entry("ops_admin", List.of("operations.manage", "platform.read", "audit.read")),
            Map.entry("api_key_admin", List.of("api-keys.manage", "api-keys.read", "api-keys.create", "api-keys.delete")),
            Map.entry("community_member", List.of("community.write", "profile.self")),
            Map.entry("user_admin", List.of("users.manage", "rbac.role.assign")),
            Map.entry("teacher", List.of("lms.teacher", "hoclieu.resource.read", "hoclieu.progress.write", "hoclieu.asset.launch")),
            Map.entry("student", List.of("lms.student"))
    );

    public Map<String, Object> decide(ErgPrincipal principal, String permission, String portal, String tenantId) {
        boolean portalAllowed = portal == null || portal.isBlank() || principal.portals().contains("*") || principal.portals().contains(portal);
        boolean tenantAllowed = tenantId == null || tenantId.isBlank() || tenantId.equals(principal.tenantId());
        boolean denied = matchesAny(expand(principal.deniedPermissions()), permission);
        boolean granted = !denied && (hasSuperAdminRole(principal) || matchesAny(expand(grants(principal)), permission));
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

    public Map<String, Object> matrix() {
        return Map.of(
                "denyOverrideWins", true,
                "wildcards", List.of("*", "lms.*", "cms.*", "hoclieu.*", "media.*", "rbac.*", "*.read"),
                "aliases", ALIASES,
                "lmsScopeEnforcedInServiceLayer", true,
                "springSecurityModel", "Spring Security filter-chain route authorization"
        );
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
        int wildcard = grant.indexOf('*');
        if (wildcard >= 0) {
            String prefix = grant.substring(0, wildcard);
            String suffix = grant.substring(wildcard + 1);
            return required.startsWith(prefix) && required.endsWith(suffix);
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
                        java.util.stream.Stream.concat(
                                ALIASES.getOrDefault(permission, List.of()).stream(),
                                ROLE_PERMISSIONS.getOrDefault(permission, List.of()).stream()
                        ).flatMap(item -> expanded(item, depth + 1).stream())
                )
                .distinct()
                .toList();
    }

    private List<String> grants(ErgPrincipal principal) {
        return java.util.stream.Stream.concat(principal.permissions().stream(), principal.roles().stream())
                .distinct()
                .toList();
    }

    private boolean hasSuperAdminRole(ErgPrincipal principal) {
        return principal.roles().stream().anyMatch(SUPER_ADMIN_ROLES::contains);
    }
}
