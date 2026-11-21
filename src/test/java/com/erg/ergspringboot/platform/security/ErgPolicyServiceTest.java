package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErgPolicyServiceTest {

    private final ErgPolicyService policy = new ErgPolicyService();

    @Test
    void wildcardGrantWithoutPlatformRoleDoesNotGrantArbitraryPermissions() {
        ErgPrincipal tenantAdmin = principal(List.of("user_admin"), List.of("*", "users.manage"), List.of());

        assertThat(policy.isAllowed(tenantAdmin, "audit.read", null, "erg")).isFalse();
        assertThat(policy.isAllowed(tenantAdmin, "users.manage", null, "erg")).isTrue();
    }

    @Test
    void wildcardGrantIsUniversalForPlatformWidePrincipal() {
        ErgPrincipal superAdmin = principal(List.of("system.super_admin"), List.of("*"), List.of());

        assertThat(policy.isAllowed(superAdmin, "audit.read", null, "erg")).isTrue();
    }

    @Test
    void wildcardDenyStillBlocksEverything() {
        ErgPrincipal denied = principal(List.of("teacher"), List.of("lms.class.read"), List.of("*"));

        assertThat(policy.isAllowed(denied, "lms.class.read", null, "erg")).isFalse();
    }

    @Test
    void prefixWildcardsKeepWorkingForOrdinaryPrincipals() {
        ErgPrincipal teacher = principal(List.of("teacher"), List.of("lms.*"), List.of());

        assertThat(policy.isAllowed(teacher, "lms.class.read", null, "erg")).isTrue();
        assertThat(policy.isAllowed(teacher, "audit.read", null, "erg")).isFalse();
    }

    @Test
    void decisionTableCoversDenyOverrideWildcardsAndAliases() {
        ErgPrincipal teacher = principal(List.of("teacher"), List.of("lms.*", "*.read", "roles.read", "content.manage"),
                List.of("lms.class.delete", "cms.posts.*"));
        Object[][] cases = {
                {"lms.class.read", true}, {"lms.class.delete", false}, {"audit.read", true}, {"audit.write", false},
                {"rbac.role.read", true}, {"rbac.permission.read", true}, {"rbac.role.create", false},
                {"cms.posts.update", false}, {"media.upload", true}, {"profile.self", true}, {"*", false}};
        for (Object[] c : cases) {
            assertThat(policy.isAllowed(teacher, (String) c[0], null, "erg")).as((String) c[0]).isEqualTo(c[1]);
        }
        assertThat(policy.isAllowedAny(teacher, null, "erg", List.of("lms.class.read", "lms.class.delete"))).isFalse();
        assertThat(policy.isAllowedAny(teacher, null, "erg", List.of("audit.write", "lms.class.read"))).isTrue();
        assertThat(policy.isAllowed(teacher, "lms.class.read", null, "other")).isFalse();
        assertThat(policy.isAllowed(null, "lms.class.read", null, "erg")).isFalse();
    }

    @Test
    void derivedGrantsAndAuthoritiesAreComputedOncePerPrincipalInstance() {
        ErgPrincipal teacher = principal(List.of("teacher"), List.of("lms.*"), List.of());

        assertThat(PrincipalDerived.of(teacher)).isSameAs(PrincipalDerived.of(teacher));
        assertThat(PrincipalDerived.of(teacher).authorities()).extracting(Object::toString)
                .containsExactly("role:teacher", "perm:lms.*");
    }

    private ErgPrincipal principal(List<String> roles, List<String> permissions, List<String> denied) {
        return new ErgPrincipal("u-1", "s-1", "erg", roles, permissions, denied, List.of(),
                "staff", "admin", Map.of());
    }
}
