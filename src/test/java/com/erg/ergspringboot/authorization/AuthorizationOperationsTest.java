package com.erg.ergspringboot.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorizationOperationsTest {
    @Test
    void delegatesAccessDecisionToThePolicyPort() {
        ErgPolicyService policy = mock(ErgPolicyService.class);
        ErgPrincipal principal = mock(ErgPrincipal.class);
        when(policy.isAllowed(principal, "calendar.read", "staff", "tenant-1")).thenReturn(true);

        AuthorizationOperations operations = new AuthorizationOperations(policy);

        assertThat(operations.isAllowed(principal, "calendar.read", "staff", "tenant-1")).isTrue();
        assertThat(operations.isAllowedAny(principal, "staff", "tenant-1", List.of("calendar.read"))).isFalse();
    }
}
