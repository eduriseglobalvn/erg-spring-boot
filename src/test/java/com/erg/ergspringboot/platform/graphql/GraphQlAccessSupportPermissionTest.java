package com.erg.ergspringboot.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.AuthenticationRequiredException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphQlAccessSupportPermissionTest {

    private final GraphQlAccessSupport access = new GraphQlAccessSupport(new ErgPolicyService());

    @Test
    void supportsPermissionWildcardsConsistentlyWithRestAuthorization() {
        ErgPrincipal principal = principal(List.of("lms.exercise.*"), List.of());

        assertThat(access.isAllowed(principal, "lms.exercise.read")).isTrue();
        assertThat(access.isAllowed(principal, "lms.exercise.update")).isTrue();
    }

    @Test
    void wildcardDenyOverridesExactAndWildcardGrants() {
        ErgPrincipal principal = principal(
                List.of("lms.*", "lms.grade.finalize"),
                List.of("lms.grade.*")
        );

        assertThat(access.isAllowed(principal, "lms.grade.finalize", "lms.manage")).isFalse();
    }

    @Test
    void tenantResolutionRequiresTheVerifiedPrincipalEvenWhenTheClientRequestsATenant() {
        assertThatThrownBy(() -> access.verifiedTenantId(null, "tenant-a"))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    private ErgPrincipal principal(List<String> permissions, List<String> denies) {
        return new ErgPrincipal(
                "teacher-1", "session-1", "erg", List.of("teacher"), permissions, denies,
                List.of("lms"), "erg", "user", Map.of()
        );
    }
}
