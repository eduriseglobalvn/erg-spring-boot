package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class PermissionAuthorizationManagerTest {

    private final PermissionAuthorizationManager manager =
            new PermissionAuthorizationManager(new ErgPolicyService());

    @Test
    void specificDenyWinsAcrossAllRequireAnyAlternatives() {
        ErgPrincipal principal = principal(
                List.of("rbac.*"),
                List.of("rbac.role.assign")
        );
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        var request = new MockHttpServletRequest("POST", "/api/v1/users/roles/bulk");

        var decision = manager.requireAny(
                () -> authentication,
                new RequestAuthorizationContext(request),
                List.of("rbac.role.assign", "rbac.*")
        );

        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void broadGrantStillAllowsWhenNoAlternativeIsDenied() {
        ErgPrincipal principal = principal(List.of("rbac.*"), List.of());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        var request = new MockHttpServletRequest("POST", "/api/v1/users/roles/bulk");

        var decision = manager.requireAny(
                () -> authentication,
                new RequestAuthorizationContext(request),
                List.of("rbac.role.assign", "rbac.*")
        );

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void roleNameAloneDoesNotCreateRuntimeAuthority() {
        ErgPrincipal principal = new ErgPrincipal(
                "actor-1", "session-1", "erg", List.of("system.rbac_admin"), List.of(), List.of(),
                List.of("*"), "erg", "admin", Map.of()
        );

        assertThat(new ErgPolicyService().isAllowed(principal, "rbac.role.delete", "admin", "erg"))
                .isFalse();
    }

    private ErgPrincipal principal(List<String> permissions, List<String> denies) {
        return new ErgPrincipal(
                "actor-1", "session-1", "erg", List.of("user_admin"), permissions, denies,
                List.of("*"), "erg", "admin", Map.of()
        );
    }
}
