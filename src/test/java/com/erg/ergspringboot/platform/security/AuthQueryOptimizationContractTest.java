package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.repository.auth.AuthSessionRepository;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
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
    void bearerPrincipalAuthorizationIsNotCrossRequestCached() throws Exception {
        Method method = ErgPrincipalService.class.getMethod("fromBearerToken", String.class, String.class);
        assertThat(method.getAnnotation(org.springframework.cache.annotation.Cacheable.class)).isNull();
    }

    @Test
    void removedCrossRequestSecurityCachesAreNotConfiguredOrInvalidated() throws IOException {
        String cacheConfig = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/config/CacheConfig.java"));
        assertThat(cacheConfig)
                .doesNotContain("descriptor(\"authPrincipal\"")
                .doesNotContain("descriptor(\"sessionBootstrap\"");

        try (var files = Files.walk(Path.of("src/main/java/com/erg/ergspringboot/service"))) {
            String services = files.filter(path -> path.toString().endsWith(".java"))
                    .map(AuthQueryOptimizationContractTest::read)
                    .reduce("", (left, right) -> left + '\n' + right);
            assertThat(services)
                    .doesNotContain("\"authPrincipal\"")
                    .doesNotContain("\"sessionBootstrap\"");
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
