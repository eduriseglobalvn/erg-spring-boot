package com.erg.ergspringboot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

@Configuration
public class OpenApiConfig {

    /**
     * Mirrors the {@code permitAll} matchers of {@code SecurityConfig}, in the same first-match order,
     * including the protected editorial variants that are matched before the broad public CMS rule.
     * {@code OpenApiConfigTest} keeps every pattern here in sync with SecurityConfig.
     */
    static final List<AccessRule> ACCESS_RULES = List.of(
            AccessRule.publicFor(null,
                    "/api/v1/health", "/api/v1/ready", "/api/health", "/api/ready", "/healthz", "/ready", "/metrics",
                    "/v3/api-docs", "/v3/api-docs/**", "/scalar", "/scalar/**",
                    "/robots.txt", "/sitemap.xml", "/sitemap_index.xml", "/sitemap-*.xml"),
            AccessRule.publicFor("POST", "/webhooks/discord", "/webhooks/telegram"),
            AccessRule.publicFor("POST", "/api/v1/auth/login", "/api/v1/auth/refresh"),
            AccessRule.publicFor("GET", "/api/v1/auth/login", "/oauth2/**", "/login/oauth2/**"),
            AccessRule.protectedFor("GET", "/api/v1/posts/hidden", "/api/v1/posts/trash", "/api/v1/posts/preview/**"),
            AccessRule.protectedFor("GET", "/api/v1/reviews/admin", "/api/v1/reviews/admin/all"),
            AccessRule.publicFor("GET",
                    "/api/v1/pages/**", "/api/v1/menus/structure",
                    "/api/v1/posts", "/api/v1/posts/**",
                    "/api/v1/courses", "/api/v1/courses/**",
                    "/api/v1/elearning/categories/**", "/api/v1/elearning/levels/**",
                    "/api/v1/recruitment/jobs/**", "/api/v1/reviews", "/api/v1/reviews/stats",
                    "/api/v1/public-disclosure/**", "/api/v1/trending/**"),
            AccessRule.publicFor("POST", "/api/v1/recruitment/apply", "/api/v1/reviews", "/api/v1/reviews/*/helpful"),
            AccessRule.publicFor("PUT", "/api/v1/lcms/media/upload/quiz-media/**")
    );

    /** Unversioned duplicates of the v1 content/curriculum API, kept only for compatibility. */
    static final List<String> DEPRECATED_PATH_PREFIXES = List.of("/api/content/", "/api/curriculum/");

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Bean
    OpenAPI ergOpenApi(@Value("${erg.openapi.server-url:/}") String serverUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("ERG Spring API")
                        .version("v1")
                        .description("Versioned ERG Spring Boot API surface")
                        .contact(new Contact().name("ERG")))
                .servers(List.of(new Server().url(serverUrl).description("Current environment")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    /** Declares {@code security: []} on public operations and deprecates the non-v1 duplicate paths. */
    @Bean
    OpenApiCustomizer ergPublicAndDeprecatedOperations() {
        return OpenApiConfig::customize;
    }

    static void customize(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            if (isPublic(method, path)) {
                operation.setSecurity(new ArrayList<>());
            }
            if (isDeprecatedDuplicate(path)) {
                operation.setDeprecated(true);
            }
        }));
    }

    static boolean isPublic(PathItem.HttpMethod method, String path) {
        String httpMethod = method.name().toUpperCase(Locale.ROOT);
        for (AccessRule rule : ACCESS_RULES) {
            if (rule.matches(httpMethod, path)) {
                return rule.publicAccess();
            }
        }
        return false;
    }

    static boolean isDeprecatedDuplicate(String path) {
        return DEPRECATED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    record AccessRule(String method, List<String> patterns, boolean publicAccess) {

        static AccessRule publicFor(String method, String... patterns) {
            return new AccessRule(method, List.of(patterns), true);
        }

        static AccessRule protectedFor(String method, String... patterns) {
            return new AccessRule(method, List.of(patterns), false);
        }

        boolean matches(String httpMethod, String path) {
            return (method == null || method.equals(httpMethod))
                    && patterns.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
        }
    }
}
