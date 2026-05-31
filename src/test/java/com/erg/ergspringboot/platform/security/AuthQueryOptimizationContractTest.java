package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.EntityGraph;

class AuthQueryOptimizationContractTest {

    @Test
    void bearerPrincipalQueryFetchesAuthorizationGraphInOneRepositoryCall() throws Exception {
        Method method = AuthSessionRepository.class.getMethod(
                "findPrincipalByAccessTokenHashAndTenantId",
                String.class,
                String.class
        );
        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);

        assertThat(entityGraph).isNotNull();
        assertThat(Arrays.asList(entityGraph.attributePaths()))
                .containsExactlyInAnyOrder(
                        "user",
                        "user.roles",
                        "user.roles.permissions",
                        "user.permissionOverrides",
                        "user.accessScopes"
                );
    }

    @Test
    void sessionBootstrapQueryFetchesAccessScopesWithUser() throws Exception {
        Method method = AuthSessionRepository.class.getMethod(
                "findWithUserBySessionIdAndTenantId",
                String.class,
                String.class
        );
        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);

        assertThat(entityGraph).isNotNull();
        assertThat(Arrays.asList(entityGraph.attributePaths())).contains("user.accessScopes");
    }

    @Test
    void bearerPrincipalCacheUnlessExpressionSupportsOptionalUnwrapping() throws Exception {
        Method method = ErgPrincipalService.class.getMethod("fromBearerToken", String.class, String.class);
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.unless()).isEqualTo("#result == null");
    }
}
