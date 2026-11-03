package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.users.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/** One table for grant/allow/deny/revoke/unknown/expired, shared by login, bearer and OIDC principals. */
class EffectivePermissionResolverTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    static Stream<Arguments> overrideTable() {
        return Stream.of(
                // grantType, expiresAt, role grants lms.class.read?, expected granted, expected denied
                Arguments.of("GRANT", null, false, true, false),
                Arguments.of("grant", null, false, true, false),
                Arguments.of("allow", null, false, true, false),
                Arguments.of(null, null, false, true, false),
                Arguments.of("DENY", null, true, false, true),
                Arguments.of("revoke", null, true, false, true),
                Arguments.of("unknown", null, true, false, true),
                Arguments.of("GRANT", NOW.minusMinutes(1), false, false, false),
                Arguments.of("DENY", NOW.minusMinutes(1), true, true, false),
                Arguments.of("DENY", NOW.plusMinutes(1), true, false, true)
        );
    }

    @ParameterizedTest(name = "{0} expires={1} roleGrant={2}")
    @MethodSource("overrideTable")
    void overrideSemantics(String grantType, OffsetDateTime expiresAt, boolean roleGrant,
                           boolean expectGranted, boolean expectDenied) {
        User user = new User();
        user.setRoles(roleGrant ? new LinkedHashSet<>(List.of(role("teacher", " LMS.Class.Read "))) : new LinkedHashSet<>());
        UserPermissionOverride override = new UserPermissionOverride();
        override.setPermission("lms.class.read");
        override.setGrantType(grantType);
        override.setExpiresAt(expiresAt);
        user.setPermissionOverrides(new LinkedHashSet<>(List.of(override)));

        EffectivePermissionResolver.Result result = EffectivePermissionResolver.resolve(user, List.of(), NOW);

        assertThat(result.granted().contains("lms.class.read")).isEqualTo(expectGranted);
        assertThat(result.denied().contains("lms.class.read")).isEqualTo(expectDenied);
    }

    @Test
    void scopedAssignmentRolesGrantButExplicitDenyStillWins() {
        User user = new User();
        user.setRoles(new LinkedHashSet<>());
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setRole(role("school_admin", "lms.report.read", "lms.class.read"));
        UserPermissionOverride deny = new UserPermissionOverride();
        deny.setPermission("lms.report.read");
        deny.setGrantType("DENY");
        user.setPermissionOverrides(new LinkedHashSet<>(List.of(deny)));

        EffectivePermissionResolver.Result result = EffectivePermissionResolver.resolve(user, List.of(assignment), NOW);

        assertThat(result.granted()).containsExactly("lms.class.read");
        assertThat(result.denied()).containsExactly("lms.report.read");
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
