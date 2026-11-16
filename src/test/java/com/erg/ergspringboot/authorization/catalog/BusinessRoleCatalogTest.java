package com.erg.ergspringboot.authorization.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class BusinessRoleCatalogTest {

    private static BusinessRole role(String id) {
        return BusinessRoleCatalog.all().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private static boolean has(BusinessRole role, PermissionKind kind) {
        return role.permissions().stream().anyMatch(name -> PermissionCatalog.kindOf(name).orElse(null) == kind);
    }

    @Test
    void hasTheSevenRoles() {
        assertThat(BusinessRoleCatalog.all()).extracting(BusinessRole::id).containsExactly(
                "role_super_admin", "role_director", "role_pdt_operator", "role_operations",
                "role_lms_teacher", "role_lms_student", "role_admissions");
        assertThat(BusinessRoleCatalog.all()).filteredOn(BusinessRole::exclusive)
                .extracting(BusinessRole::id).containsExactlyInAnyOrder("role_director", "role_operations");
    }

    @Test
    void everyGrantedPermissionIsInTheCatalog() {
        for (BusinessRole role : BusinessRoleCatalog.all()) {
            for (String name : role.permissions()) {
                assertThat(PermissionCatalog.kindOf(name)).as(role.id() + " " + name).isPresent();
            }
        }
    }

    @Test
    void directorAndOperationsHaveNoSuaPermission() {
        assertThat(has(role("role_director"), PermissionKind.SUA)).isFalse();
        assertThat(has(role("role_operations"), PermissionKind.SUA)).isFalse();
        assertThat(has(role("role_director"), PermissionKind.XEM)).isTrue();
    }

    @Test
    void pdtHasNoCrmOrAuditOrActivity() {
        Set<String> pdt = role("role_pdt_operator").permissions();
        assertThat(pdt).noneMatch(n -> n.startsWith("crm.") || n.startsWith("audit."))
                .doesNotContain("users.activity.read", "api-keys.manage", "*", "crm.workspace.access");
        assertThat(pdt).contains("admin.workspace.access", "elearning.student.preview", "users.manage", "rbac.role.assign");
    }

    @Test
    void onlySuperAdminHasWildcard() {
        for (BusinessRole role : BusinessRoleCatalog.all()) {
            assertThat(role.permissions().contains("*")).as(role.id())
                    .isEqualTo(role.id().equals("role_super_admin"));
        }
    }

    @Test
    void operationsSeesLmsButNotCrmAuditOrAuthorization() {
        Set<String> ops = role("role_operations").permissions();
        assertThat(ops).contains("users.read", "lms.class.read", "timekeeping.read", "admin.workspace.access");
        assertThat(ops).noneMatch(n -> n.startsWith("crm.") || n.startsWith("audit.") || n.startsWith("rbac."));
        assertThat(ops).doesNotContain("users.activity.read", "crm.workspace.access");
    }

    @Test
    void gatesFollowTheSpecTable() {
        assertThat(role("role_director").permissions()).contains("admin.workspace.access", "crm.workspace.access")
                .doesNotContain("teacher.workspace.access", "student.workspace.access");
        assertThat(role("role_lms_teacher").permissions()).contains("teacher.workspace.access")
                .doesNotContain("admin.workspace.access");
        assertThat(role("role_lms_student").permissions()).contains("student.workspace.access");
        assertThat(role("role_admissions").permissions()).contains("crm.workspace.access")
                .doesNotContain("admin.workspace.access");
    }

    @Test
    void matrixCellsReflectKinds() {
        assertThat(BusinessRoleCatalog.cell(role("role_director"), PermissionFunction.CRM)).isEqualTo("XEM");
        assertThat(BusinessRoleCatalog.cell(role("role_pdt_operator"), PermissionFunction.CRM)).isEqualTo("NONE");
        assertThat(BusinessRoleCatalog.cell(role("role_pdt_operator"), PermissionFunction.STUDENT)).isEqualTo("SUA");
        assertThat(BusinessRoleCatalog.cell(role("role_super_admin"), PermissionFunction.AUDIT)).isEqualTo("SUA");
    }

    @Test
    void snapshotMatches() throws IOException {
        StringBuilder actual = new StringBuilder();
        for (BusinessRole role : BusinessRoleCatalog.all()) {
            actual.append("## ").append(role.id()).append('\n');
            for (String name : new TreeSet<>(role.permissions())) {
                actual.append(name).append('\n');
            }
            actual.append('\n');
        }
        try (InputStream in = getClass().getResourceAsStream("/rbac/role-permissions.snapshot.txt")) {
            assertThat(in).as("snapshot resource").isNotNull();
            String expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            // To accept an intended change, regenerate src/test/resources/rbac/role-permissions.snapshot.txt.
            assertThat(actual.toString()).isEqualTo(expected);
        }
    }
}
