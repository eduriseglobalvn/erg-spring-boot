package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.authorization.AuthorizationOperations;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class MethodAuthorizationEvaluatorTest {

    private final MethodAuthorizationEvaluator evaluator = new MethodAuthorizationEvaluator(
            new AuthorizationOperations(new ErgPolicyService()));

    @Test
    void acceptsWildcardAndExpandedAliasThroughTheCanonicalPolicy() {
        assertThat(evaluator.isAllowedAny(authentication(principal(List.of("*"), List.of())),
                "lms.schedule.read")).isTrue();
        assertThat(evaluator.isAllowedAny(authentication(principal(List.of("roles.read"), List.of())),
                "rbac.role.read")).isTrue();
    }

    @Test
    void explicitDenyWinsAcrossAlternativePermissions() {
        ErgPrincipal principal = principal(
                List.of("*", "lms.schedule.read"),
                List.of("lms.schedule.read"));

        assertThat(evaluator.isAllowedAny(authentication(principal),
                "lms.schedule.read", "lms.manage")).isFalse();
    }

    @Test
    void rejectsMissingOrForeignPrincipal() {
        assertThat(evaluator.isAllowedAny(null, "lms.schedule.read")).isFalse();
        assertThat(evaluator.isAllowedAny(
                UsernamePasswordAuthenticationToken.authenticated("user", "n/a", List.of()),
                "lms.schedule.read")).isFalse();
    }

    @Test
    void rejectsPrincipalWithoutVerifiedTenantIdentity() {
        ErgPrincipal tenantless = ErgPrincipal.authenticated(
                "user-1", "session-1", " ", List.of(), List.of("*"), List.of(),
                List.of("*"), "staff", "staff", Map.of());

        assertThat(evaluator.isAllowedAny(authentication(tenantless), "lms.schedule.read")).isFalse();
    }

    private UsernamePasswordAuthenticationToken authentication(ErgPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a", List.of());
    }

    private ErgPrincipal principal(List<String> permissions, List<String> deniedPermissions) {
        return ErgPrincipal.authenticated(
                "user-1", "session-1", "tenant-1",
                List.of("system.super_admin"), permissions, deniedPermissions, List.of("*"),
                "staff", "staff", Map.of());
    }
}
