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

    /** J: schedule-derived class access (2-week rule) is not in the principal; only standing scopes are. */
    @Test
    void classScopeFollowsStandingScopesOnlyAndASchoolScopeCoversEveryClassOfThatSchool() {
        ErgPrincipal noStandingScope = scoped(List.of(), List.of());
        assertThatThrownBy(() -> access.assertClassScope(noStandingScope, "class-lapsed", "school-1"))
                .isInstanceOf(com.erg.ergspringboot.exception.ForbiddenException.class);

        ErgPrincipal classOnly = scoped(List.of(), List.of("class-a"));
        access.assertClassScope(classOnly, "class-a", null);
        assertThatThrownBy(() -> access.assertClassScope(classOnly, "class-b", "school-1"))
                .isInstanceOf(com.erg.ergspringboot.exception.ForbiddenException.class);

        ErgPrincipal schoolScope = scoped(List.of("school-1"), List.of());
        access.assertClassScope(schoolScope, "class-lapsed", "school-1");
        assertThatThrownBy(() -> access.assertClassScope(schoolScope, "class-lapsed", null))
                .isInstanceOf(com.erg.ergspringboot.exception.ForbiddenException.class);
        assertThatThrownBy(() -> access.assertClassScope(schoolScope, "class-other", "school-2"))
                .isInstanceOf(com.erg.ergspringboot.exception.ForbiddenException.class);
    }

    private ErgPrincipal scoped(List<String> schools, List<String> classes) {
        return new ErgPrincipal("teacher-1", "session-1", "erg", List.of("teacher"), List.of("lms.class_log.read"), List.of(),
                List.of("lms"), "erg", "user",
                Map.of("managedEducationUnitIds", schools, "managedClassIds", classes));
    }

    private ErgPrincipal principal(List<String> permissions, List<String> denies) {
        return new ErgPrincipal(
                "teacher-1", "session-1", "erg", List.of("teacher"), permissions, denies,
                List.of("lms"), "erg", "user", Map.of()
        );
    }
}
