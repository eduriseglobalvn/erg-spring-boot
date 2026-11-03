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

    private ErgPrincipal principal(List<String> roles, List<String> permissions, List<String> denied) {
        return new ErgPrincipal("u-1", "s-1", "erg", roles, permissions, denied, List.of(),
                "staff", "admin", Map.of());
    }
}
