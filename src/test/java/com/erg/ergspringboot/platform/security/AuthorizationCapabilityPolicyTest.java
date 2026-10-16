package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorizationCapabilityPolicyTest {

    private final AuthorizationCapabilityPolicy policy = new AuthorizationCapabilityPolicy();

    @Test
    void derivesAllWorkspacesFromVerifiedMultiRoleGrants() {
        ErgPrincipal principal = principal(
                List.of("admin", "teacher", "student", "crm_manager"),
                List.of("admin.content.read", "lms.schedule.read", "elearning.dashboard.read", "crm.lead.read"),
                List.of(), List.of("*")
        );

        assertThat(WorkspaceCapabilityMapper.canonicalWorkspacesFromVerifiedGrants(
                principal.roles(), principal.permissions(), principal.deniedPermissions()))
                .containsExactly("ADMIN", "CRM", "STUDENT", "TEACHER");
        assertThat(policy.hasWorkspace(principal, "admin")).isTrue();
        assertThat(policy.hasWorkspace(principal, "lms")).isTrue();
        assertThat(policy.hasWorkspace(principal, "elearning")).isTrue();
        assertThat(policy.hasWorkspace(principal, "crm")).isTrue();
    }

    @Test
    void legacyPortalListCannotGrantAWorkspace() {
        ErgPrincipal principal = principal(
                List.of("auditor"), List.of("reports.read"), List.of(), List.of("*")
        );

        assertThat(policy.hasWorkspace(principal, "admin")).isFalse();
        assertThat(policy.hasWorkspace(principal, "lms")).isFalse();
    }

    @Test
    void wildcardRequiresVerifiedSystemRoleAndExplicitDenyWins() {
        ErgPrincipal system = principal(
                List.of("system.super_admin"), List.of("*"), List.of(), List.of()
        );
        ErgPrincipal unprivileged = principal(
                List.of("teacher"), List.of("*"), List.of(), List.of("*")
        );
        ErgPrincipal denied = principal(
                List.of("admin", "teacher"), List.of("admin.content.read", "lms.schedule.read"),
                List.of("admin.*"), List.of("*")
        );

        assertThat(policy.isPlatformWide(system)).isTrue();
        assertThat(policy.hasWorkspace(system, "crm")).isTrue();
        assertThat(policy.isPlatformWide(unprivileged)).isFalse();
        assertThat(policy.hasWorkspace(unprivileged, "admin")).isFalse();
        assertThat(policy.hasWorkspace(denied, "admin")).isFalse();
        assertThat(policy.hasWorkspace(denied, "lms")).isTrue();
    }

    @Test
    void tenantAndScopeChecksFailClosed() {
        ErgPrincipal principal = principal(
                List.of("teacher"), List.of("lms.schedule.read"), List.of(), List.of("lms")
        );
        assertThat(policy.tenantMatches(principal, "other-tenant")).isFalse();
        assertThat(policy.tenantMatches(principal, "tenant-a")).isTrue();
        assertThat(policy.canAccessScope(principal, AccessScopeSet.EDUCATION_UNIT, "unit-a")).isFalse();

        ErgPrincipal scoped = new ErgPrincipal(
                "u-1", "s-1", "tenant-a", List.of("teacher"), List.of("lms.schedule.read"), List.of(),
                List.of("lms"), "erg", "user",
                Map.of("managedEducationUnitIds", List.of("unit-a"))
        );
        assertThat(policy.canAccessScope(scoped, AccessScopeSet.EDUCATION_UNIT, "unit-a")).isTrue();
        assertThat(policy.canAccessScope(scoped, AccessScopeSet.EDUCATION_UNIT, "unit-b")).isFalse();
    }

    @Test
    void authenticationAndPolicyNoLongerAuthorizeFromPortalList() throws Exception {
        String filter = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/ErgAuthenticationFilter.java"));
        String policySource = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/security/ErgPolicyService.java"));
        assertThat(filter).doesNotContain("principal.portals().contains(\"*\")");
        assertThat(policySource).doesNotContain("principal.portals().contains");
        assertThat(policySource).contains("AuthorizationCapabilityPolicy");
    }

    private ErgPrincipal principal(
            List<String> roles, List<String> permissions, List<String> denied, List<String> portals) {
        return new ErgPrincipal("u-1", "s-1", "tenant-a", roles, permissions, denied,
                portals, "erg", "user", Map.of());
    }
}
