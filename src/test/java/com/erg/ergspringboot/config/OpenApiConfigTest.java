package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void publicOperationsDeclareEmptySecurityAndEditorialVariantsStayProtected() throws Exception {
        OpenAPI openApi = customizedSnapshot();

        assertThat(operation(openApi, "/api/v1/auth/login", HttpMethod.POST).getSecurity()).isEmpty();
        assertThat(operation(openApi, "/api/v1/auth/refresh", HttpMethod.POST).getSecurity()).isEmpty();
        assertThat(operation(openApi, "/api/v1/health", HttpMethod.GET).getSecurity()).isEmpty();
        assertThat(operation(openApi, "/webhooks/telegram", HttpMethod.POST).getSecurity()).isEmpty();
        assertThat(OpenApiConfig.isPublic(HttpMethod.GET, "/api/v1/posts/{slug}")).isTrue();
        assertThat(Json.mapper().writeValueAsString(operation(openApi, "/api/v1/auth/login", HttpMethod.POST)))
                .contains("\"security\":[]");

        assertThat(OpenApiConfig.isPublic(HttpMethod.GET, "/api/v1/posts/hidden")).isFalse();
        assertThat(OpenApiConfig.isPublic(HttpMethod.GET, "/api/v1/reviews/admin")).isFalse();
        assertThat(OpenApiConfig.isPublic(HttpMethod.POST, "/api/v1/posts")).isFalse();
        assertThat(OpenApiConfig.isPublic(HttpMethod.GET, "/api/v1/users/me")).isFalse();
        assertThat(OpenApiConfig.isPublic(HttpMethod.POST, "/webhooks/telegram")).isTrue();
        assertThat(OpenApiConfig.isPublic(HttpMethod.POST, "/api/v1/reviews/{reviewId}/helpful")).isTrue();
        assertThat(operation(openApi, "/api/v1/users/me", HttpMethod.GET).getSecurity()).isNull();
    }

    @Test
    void nonVersionedContentAndCurriculumDuplicatesAreDeprecated() throws Exception {
        OpenAPI openApi = customizedSnapshot();

        List<String> deprecatedPaths = openApi.getPaths().entrySet().stream()
                .filter(entry -> entry.getValue().readOperations().stream()
                        .anyMatch(op -> Boolean.TRUE.equals(op.getDeprecated())))
                .map(java.util.Map.Entry::getKey)
                .filter(OpenApiConfig::isDeprecatedDuplicate)
                .toList();
        assertThat(deprecatedPaths).hasSize(9);
        openApi.getPaths().forEach((path, item) -> {
            if (OpenApiConfig.isDeprecatedDuplicate(path)) {
                assertThat(item.readOperations()).allMatch(op -> Boolean.TRUE.equals(op.getDeprecated()));
            }
        });
        assertThat(openApi.getPaths().get("/api/v1/content/items").readOperations())
                .noneMatch(op -> Boolean.TRUE.equals(op.getDeprecated()));
    }

    @Test
    void everyMirroredPatternExistsInSecurityConfig() throws Exception {
        String securityConfig = Files.readString(
                Path.of("src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java"));

        OpenApiConfig.ACCESS_RULES.stream()
                .flatMap(rule -> rule.patterns().stream())
                .forEach(pattern -> assertThat(securityConfig)
                        .as("SecurityConfig still matches %s", pattern)
                        .contains("\"" + pattern + "\""));
    }

    private static OpenAPI customizedSnapshot() throws Exception {
        OpenAPI openApi = Json.mapper().readValue(
                Path.of("contracts/openapi/erg-openapi.json").toFile(), OpenAPI.class);
        OpenApiConfig.customize(openApi);
        return openApi;
    }

    private static Operation operation(OpenAPI openApi, String path, HttpMethod method) {
        return Objects.requireNonNull(
                openApi.getPaths().get(path).readOperationsMap().get(method), path + " " + method);
    }
}
