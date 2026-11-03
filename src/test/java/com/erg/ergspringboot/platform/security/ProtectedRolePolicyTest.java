package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.authorization.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProtectedRolePolicyTest {

    private final ProtectedRolePolicy policy = new ProtectedRolePolicy();

    @Test
    void rejectsCreationOrRenameIntoReservedSystemNamespace() {
        assertThatThrownBy(() -> policy.assertRoleDefinitionAllowed(" SyStEm.Super_Admin "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void delegatedAdministratorCannotGrantProtectedRole() {
        ErgPrincipal actor = principal(
                List.of("user_admin"),
                List.of("users.manage", "rbac.role.assign")
        );

        assertThatThrownBy(() -> policy.assertCanGrant(actor, List.of(role("system.super_admin"))))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("protected role");
    }

    @Test
    void protectedRoleNameWithoutDatabaseWildcardCannotGrantProtectedRole() {
        ErgPrincipal actor = principal(List.of("system.super_admin"), List.of("rbac.role.assign"));

        assertThatThrownBy(() -> policy.assertCanGrant(actor, List.of(role("system.super_admin"))))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("protected role");
    }

    @Test
    void platformSuperAdminCanGrantExistingProtectedRole() {
        ErgPrincipal actor = principal(List.of("system.super_admin"), List.of("*"));

        assertThatCode(() -> policy.assertCanGrant(actor, List.of(role("system.super_admin"))))
                .doesNotThrowAnyException();
    }

    @Test
    void godModeRemainsUnassignableUntilStepUpAuthenticationExists() {
        ErgPrincipal actor = principal(List.of("system.super_admin"), List.of("*"));

        assertThatThrownBy(() -> policy.assertCanGrant(actor, List.of(role("system.god_mode"))))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("step-up authentication");
    }

    @Test
    void tenantAdminWithWildcardPermissionButNoSystemRoleIsNotPlatformWide() {
        ErgPrincipal actor = principal(List.of("user_admin"), List.of("*"));

        assertThatThrownBy(() -> policy.assertCanGrant(actor, List.of(role("system.super_admin"))))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> policy.assertCanManageRoleDefinitions(actor))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delegatedAdministratorCannotGrantWildcardOrRbacPermissions() {
        ErgPrincipal actor = principal(List.of("user_admin"), List.of("users.manage", "rbac.role.assign"));

        assertThatThrownBy(() -> policy.assertCanGrantPermissions(actor, "user-2", List.of("*")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> policy.assertCanGrantPermissions(actor, "user-2", List.of("rbac.role.assign")))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> policy.assertCanGrantPermissions(actor, "user-2", List.of("lms.class.read")))
                .doesNotThrowAnyException();
    }

    @Test
    void nobodyCanGrantPermissionsToThemselves() {
        ErgPrincipal superAdmin = principal(List.of("system.super_admin"), List.of("*"));

        assertThatThrownBy(() -> policy.assertCanGrantPermissions(superAdmin, "actor-1", List.of("lms.class.read")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("self");
        assertThatThrownBy(() -> policy.assertCanGrantPermissions(null, "user-2", List.of("lms.class.read")))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> policy.assertCanGrantPermissions(superAdmin, "user-2", List.of("*")))
                .doesNotThrowAnyException();
    }

    private ErgPrincipal principal(List<String> roles, List<String> permissions) {
        return new ErgPrincipal(
                "actor-1", "session-1", "erg", roles, permissions, List.of(), List.of("*"),
                "erg", "admin", Map.of()
        );
    }

    private Role role(String name) {
        Role role = new Role();
        role.setId("role-" + name);
        role.setName(name);
        return role;
    }
}
