package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProtectedRolePolicyTest {

    private final ProtectedRolePolicy policy = new ProtectedRolePolicy();

    @Test
    void roleNameMustNotBeBlank() {
        assertThatThrownBy(() -> policy.assertRoleDefinitionAllowed(" "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void actorCannotGrantRoleCarryingPermissionTheyLack() {
        ErgPrincipal actor = principal(List.of("pdt_operator"), List.of("users.manage", "lms.class.manage"));

        assertThatThrownBy(() -> policy.assertCanGrant(actor, List.of(role("crm_role", "crm.workspace.access"))))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> policy.assertCanGrant(actor, List.of(role("teacher", "lms.class.manage"))))
                .doesNotThrowAnyException();
    }

    @Test
    void wildcardRoleOnlyFromPlatformSuperAdmin() {
        Role admin = role("system.super_admin", "*");

        assertThatThrownBy(() -> policy.assertCanGrant(principal(List.of("pdt_operator"), List.of("*")), List.of(admin)))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> policy.assertCanGrant(principal(List.of("system.super_admin"), List.of("*")), List.of(admin)))
                .doesNotThrowAnyException();
    }

    @Test
    void onlyPlatformWideActorManagesRoleDefinitions() {
        assertThatThrownBy(() -> policy.assertCanManageRoleDefinitions(principal(List.of("pdt_operator"), List.of("*"))))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> policy.assertCanManageRoleDefinitions(principal(List.of("system.super_admin"), List.of("*"))))
                .doesNotThrowAnyException();
    }

    private ErgPrincipal principal(List<String> roles, List<String> permissions) {
        return new ErgPrincipal(
                "actor-1", "session-1", "erg", roles, permissions, List.of(), List.of("*"),
                "erg", "admin", Map.of()
        );
    }

    private Role role(String name, String... permissions) {
        Role role = new Role();
        role.setId("role-" + name);
        role.setName(name);
        LinkedHashSet<Permission> values = new LinkedHashSet<>();
        for (String value : permissions) {
            Permission permission = new Permission();
            permission.setName(value);
            values.add(permission);
        }
        role.setPermissions(values);
        return role;
    }
}
