package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraphQlEnterpriseSecurityAuditTest {

    @Test
    void graphQlSecurityCoveragePinsNoTokenPortalSpoofDeniedPermissionAndEmptyScopeCases() throws IOException {
        String securityGuardTest = read("src/test/java/com/erg/ergspringboot/platform/security/GraphQlSecurityGuardContractTest.java");
        String securityConfig = read("src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java");
        String contextFactoryTest = read("src/test/java/com/erg/ergspringboot/platform/graphql/GraphQlRequestContextFactoryTest.java");
        String resourceReadServiceTest = read("src/test/java/com/erg/ergspringboot/service/learningcontent/LearningResourceLibraryReadServiceTest.java");

        assertThat(securityGuardTest)
                .contains("securityConfigRequiresAuthenticatedPostToGraphQlEndpoint")
                .contains("graphQlIntrospectionIsRejectedWhenDisabled")
                .contains("oversizedVariableArraysAreRejectedBeforeResolverExecution");
        assertThat(securityConfig)
                .contains("HttpMethod.POST")
                .contains("/api/v1/graphql")
                .contains("authenticated()");

        assertThat(contextFactoryTest)
                .contains("rejectsTenantHeaderThatDoesNotMatchPrincipalTenant")
                .contains("rejectsPortalHeaderThatPrincipalCannotAccess")
                .contains("rejectsMissingVerifiedPrincipal");

        assertThat(resourceReadServiceTest)
                .contains("readsPublishedCanonicalResourcesWhenActiveLibraryRequested")
                .contains("filtersCanonicalResourcesByLevelBeforePagination");
    }

    @Test
    void graphQlContextMustNotStoreRawBearerToken() throws IOException {
        String context = read("src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlRequestContext.java");
        String factory = read("src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlRequestContextFactory.java");

        assertThat(context)
                .contains("boolean authorizationPresent")
                .doesNotContain("String authorization")
                .doesNotContain("String bearer")
                .doesNotContain("String token");
        assertThat(factory)
                .contains("hasBearer(headers)")
                .doesNotContain("getFirst(HttpHeaders.AUTHORIZATION),");
    }

    @Test
    void graphQlOpsGuardSettingsHaveRuntimeDepthComplexityAndRateLimitGuards() throws IOException {
        String applicationYaml = read("src/main/resources/application.yml");
        String guardFilter = read("src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlGuardFilter.java");
        String executionGuards = read("src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlExecutionGuardConfiguration.java");
        String deadlineInstrumentation = read("src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlDeadlineInstrumentation.java");
        String rateLimiter = read("src/main/java/com/erg/ergspringboot/platform/graphql/GraphQlRateLimiter.java");

        assertThat(applicationYaml)
                .contains("max-depth:")
                .contains("max-complexity:")
                .contains("execution-timeout:")
                .contains("rate-limit:");
        assertThat(guardFilter)
                .contains("maxDepth")
                .contains("getMaxAliases")
                .contains("aliasCount")
                .contains("isRejectSensitiveVariables")
                .contains("containsSensitiveVariable")
                .contains("Retry-After");
        assertThat(executionGuards)
                .contains("MaxQueryDepthInstrumentation")
                .contains("MaxQueryComplexityInstrumentation")
                .contains("GraphQlDeadlineInstrumentation");
        assertThat(deadlineInstrumentation)
                .contains("AbortExecutionException")
                .contains("instrumentDataFetcher");
        assertThat(rateLimiter)
                .contains("GraphQlRateLimitCounterRouter")
                .contains("LocalEmergencyRateLimitCounter")
                .contains("isFailClosedOnRedisError")
                .contains("erg:state:v1:graphql-rate-limit:")
                .contains("getRequestsPerMinute")
                .contains("GraphQlRateLimitKey");
    }

    @Test
    void persistenceTimeoutsAreConfiguredToComplementGraphQlDeadline() throws IOException {
        String applicationYaml = read("src/main/resources/application.yml");

        assertThat(applicationYaml)
                .contains("SPRING_JPA_HIBERNATE_JDBC_TIMEOUT_SECONDS")
                .contains("SPRING_JPA_QUERY_TIMEOUT_MS")
                .contains("timeout: ${SPRING_JPA_HIBERNATE_JDBC_TIMEOUT_SECONDS:5}")
                .contains("timeout: ${SPRING_JPA_QUERY_TIMEOUT_MS:5000}");
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
