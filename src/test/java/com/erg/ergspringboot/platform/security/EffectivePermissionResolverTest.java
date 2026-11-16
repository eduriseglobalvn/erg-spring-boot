package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.users.persistence.authorization.Permission;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.authorization.UserPermissionOverride;
import com.erg.ergspringboot.users.persistence.authorization.UserRoleAssignment;
import com.erg.ergspringboot.users.persistence.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Effective permissions come from roles only; per-user overrides are retired. */
class EffectivePermissionResolverTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void permissionOverridesAreIgnored() {
        User user = new User();
        user.setRoles(new LinkedHashSet<>(List.of(role("teacher", " LMS.Class.Read "))));
        UserPermissionOverride deny = new UserPermissionOverride();
        deny.setPermission("lms.class.read");
        deny.setGrantType("DENY");
        UserPermissionOverride grant = new UserPermissionOverride();
        grant.setPermission("lms.report.read");
        grant.setGrantType("GRANT");
        user.setPermissionOverrides(new LinkedHashSet<>(List.of(deny, grant)));

        EffectivePermissionResolver.Result result = EffectivePermissionResolver.resolve(user, List.of(), NOW);

        assertThat(result.granted()).containsExactly("lms.class.read");
        assertThat(result.denied()).isEmpty();
    }

    @Test
    void scopedAssignmentRolesGrantTheirPermissions() {
        User user = new User();
        user.setRoles(new LinkedHashSet<>());
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setRole(role("school_admin", "lms.report.read", "lms.class.read"));

        EffectivePermissionResolver.Result result = EffectivePermissionResolver.resolve(user, List.of(assignment), NOW);

        assertThat(result.granted()).containsExactly("lms.class.read", "lms.report.read");
    }

    @Test
    void educationUnitScopeTypesAreSharedAndCaseInsensitive() {
        for (String type : List.of("SCHOOL", "school", "Center", "SYSTEM", "organization")) {
            assertThat(AccessScopeAssembler.isEducationUnitScopeType(type)).as(type).isTrue();
        }
        assertThat(AccessScopeAssembler.isEducationUnitScopeType("CLASS")).isFalse();
        assertThat(AccessScopeAssembler.isEducationUnitScopeType(null)).isFalse();
    }

    private Role role(String name, String... permissions) {
        Role role = new Role();
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
