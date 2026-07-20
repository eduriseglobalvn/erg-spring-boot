package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ActiveProfiles({"test", "prototype"})
@SpringBootTest(properties = {
        "erg.auth.bff-enabled=true",
        "erg.process-local.enabled=true",
        "spring.security.oauth2.client.registration.erg-api.provider=keycloak",
        "spring.security.oauth2.client.registration.erg-api.client-id=catalog-test",
        "spring.security.oauth2.client.registration.erg-api.client-secret=catalog-test",
        "spring.security.oauth2.client.registration.erg-api.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.erg-api.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.erg-api.scope=openid,profile,email",
        "spring.security.oauth2.client.provider.keycloak.authorization-uri=http://localhost/catalog/auth",
        "spring.security.oauth2.client.provider.keycloak.token-uri=http://localhost/catalog/token",
        "spring.security.oauth2.client.provider.keycloak.jwk-set-uri=http://localhost/catalog/jwks",
        "spring.security.oauth2.client.provider.keycloak.user-info-uri=http://localhost/catalog/userinfo",
        "spring.security.oauth2.client.provider.keycloak.user-name-attribute=sub"
})
class ApiCoverageCatalogIntegrationTest {

    private static final Path CATALOG = Path.of("src/test/resources/performance/api-performance-catalog.yaml");
    private static final Set<String> REQUIRED_ROUTE_METADATA = Set.of(
            "classification",
            "statement-budget",
            "row-budget",
            "payload-budget-bytes",
            "state-owner",
            "cache-policy",
            "pagination-policy");
    private static final Set<String> ALLOWED_CLASSIFICATIONS = Set.of(
            "persistent-read", "persistent-write", "external-io",
            "process-local-stub", "framework", "unsupported");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void catalogExactlyMatchesRuntimeMethodPathHandlerMappings() throws IOException {
        Set<String> actual = runtimeRoutes();
        Set<String> cataloged = catalogRoutes();

        if (!actual.equals(cataloged)) {
            System.out.println("ROUTE_CATALOG_BEGIN");
            actual.forEach(route -> System.out.println("    - route: " + route));
            System.out.println("ROUTE_CATALOG_END");
        }

        assertThat(cataloged)
                .as("runtime REST route catalog must contain every normalized method/path/handler exactly once")
                .containsExactlyElementsOf(actual);
    }

    @Test
    void everyRuntimeRouteHasAllRequiredPerformanceMetadata() throws IOException {
        List<String> lines = Files.readAllLines(CATALOG);
        Set<String> violations = new LinkedHashSet<>();
        String route = null;
        Map<String, Integer> metadata = new TreeMap<>();
        Map<String, String> metadataValues = new TreeMap<>();
        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.startsWith("- route: ")) {
                addMetadataViolation(violations, route, metadata, metadataValues);
                route = line.substring("- route: ".length()).trim();
                metadata = new TreeMap<>();
                metadataValues = new TreeMap<>();
            } else if (route != null && !line.isBlank() && !line.equals("graphql:") && line.contains(":")) {
                String key = line.substring(0, line.indexOf(':')).trim();
                metadata.merge(key, 1, Integer::sum);
                metadataValues.putIfAbsent(key, line.substring(line.indexOf(':') + 1).trim());
            }
            if (line.equals("graphql:")) {
                addMetadataViolation(violations, route, metadata, metadataValues);
                route = null;
                metadata = new TreeMap<>();
                metadataValues = new TreeMap<>();
            }
        }
        addMetadataViolation(violations, route, metadata, metadataValues);

        assertThat(violations)
                .as("every runtime route requires classification, SQL/row/payload budgets, state, cache, and pagination policy")
                .isEmpty();
    }

    private void addMetadataViolation(
            Set<String> violations,
            String route,
            Map<String, Integer> metadata,
            Map<String, String> metadataValues) {
        if (route == null) return;
        Set<String> missing = new TreeSet<>(REQUIRED_ROUTE_METADATA);
        missing.removeAll(metadata.keySet());
        if (!missing.isEmpty()) violations.add(route + " missing " + missing);
        REQUIRED_ROUTE_METADATA.stream()
                .filter(key -> metadata.getOrDefault(key, 0) > 1)
                .forEach(key -> violations.add(route + " duplicates " + key));
        String classification = metadataValues.get("classification");
        if (classification != null && !ALLOWED_CLASSIFICATIONS.contains(classification)) {
            violations.add(route + " has unsupported classification " + classification);
        }
        if ("process-local-stub".equals(classification)
                && !"disable-in-prod".equals(metadataValues.get("disposition"))) {
            violations.add(route + " process-local-stub requires disposition disable-in-prod");
        }
    }

    private Set<String> runtimeRoutes() {
        Set<String> routes = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handler) -> addRoutes(routes, mapping, handler));
        return routes;
    }

    private void addRoutes(Set<String> routes, RequestMappingInfo mapping, HandlerMethod handler) {
        if (!handler.getBeanType().getPackageName().startsWith("com.erg.ergspringboot.controller")) return;
        Set<String> paths = mapping.getPatternValues();
        RequestMethodsRequestCondition methodCondition = mapping.getMethodsCondition();
        Set<org.springframework.web.bind.annotation.RequestMethod> methods = methodCondition.getMethods();
        String handlerName = handler.getBeanType().getName() + '.' + handler.getMethod().getName();
        if (methods.isEmpty()) {
            paths.forEach(path -> routes.add("ANY " + normalizePath(path) + " => " + handlerName));
            return;
        }
        methods.forEach(method -> paths.forEach(path ->
                routes.add(method.name() + ' ' + normalizePath(path) + " => " + handlerName)));
    }

    private Set<String> catalogRoutes() throws IOException {
        Set<String> routes = new TreeSet<>();
        Files.readAllLines(CATALOG).stream()
                .map(String::strip)
                .filter(line -> line.startsWith("- route: "))
                .map(line -> line.substring("- route: ".length()).trim())
                .forEach(routes::add);
        return routes;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        String normalized = path.replaceAll("/+", "/");
        if (!normalized.startsWith("/")) normalized = '/' + normalized;
        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }
}
