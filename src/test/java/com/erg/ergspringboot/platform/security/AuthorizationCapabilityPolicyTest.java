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
    void learnerReachesTheLmsApiWhileHoldingOnlyTheStudentWorkspace() {
        // The grants role_lms_student holds (V4, V17, V84, V116, V136).
        ErgPrincipal learner = principal(
                List.of("lms.learner"),
                List.of("lms.assignment.read_self", "lms.assignment.submit", "lms.schedule.read_self", "lms.quiz.play",
                        "elearning.assignment.read_self", "elearning.dashboard.read"),
                List.of(), List.of()
        );

        assertThat(WorkspaceCapabilityMapper.canonicalWorkspacesFromVerifiedGrants(
                learner.roles(), learner.permissions(), learner.deniedPermissions()))
                .containsExactly("STUDENT");
        // A learner's assignments, quiz packages and attempts are served under /api/v1/lms.
        assertThat(policy.hasWorkspace(learner, "lms")).isTrue();
        assertThat(policy.hasWorkspace(learner, "elearning")).isTrue();
        assertThat(policy.hasWorkspace(learner, "admin")).isFalse();
        assertThat(policy.hasWorkspace(learner, "lcms")).isFalse();
        assertThat(policy.hasWorkspace(learner, "crm")).isFalse();
    }

    @Test
    void teacherDoesNotGainTheStudentApiFromTheSharedLmsApi() {
        ErgPrincipal teacher = principal(
                List.of("lms.instructor"), List.of("lms.class.read"), List.of(), List.of()
        );

        assertThat(policy.hasWorkspace(teacher, "lms")).isTrue();
        assertThat(policy.hasWorkspace(teacher, "elearning")).isFalse();
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
