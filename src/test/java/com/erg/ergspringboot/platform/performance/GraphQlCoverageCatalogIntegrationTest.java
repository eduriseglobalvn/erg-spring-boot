package com.erg.ergspringboot.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Exact schema/controller/catalog drift gate for GraphQL roots, business fields, and mutations. */
class GraphQlCoverageCatalogIntegrationTest {
    private static final Path SCHEMA = Path.of("src/main/resources/graphql/schema.graphqls");
    private static final Path CATALOG = Path.of("src/test/resources/performance/graphql-performance-catalog.yaml");
    private static final Set<String> BUSINESS_TYPES = Set.of("Query", "LmsQuery", "LcmsQuery", "CrmQuery", "ElearningQuery", "Mutation");
    private static final Set<String> SHELL_FIELDS = Set.of("namespace", "status");
    private static final Set<String> REQUIRED_METADATA = Set.of(
            "owner", "auth", "classification", "risk", "statement-budget",
            "row-budget", "payload-budget-bytes", "latency-budget-ms", "selection-scenario",
            "cacheability", "cache-ttl-seconds", "cache-scope", "generation-domain",
            "cold-warm-scenario", "fixture-id");
    private static final Pattern SCHEMA_TYPE = Pattern.compile("^(?:extend\\s+)?type\\s+([A-Za-z][A-Za-z0-9_]*)\\b");
    private static final Pattern SCHEMA_FIELD = Pattern.compile("^\\s{2}([A-Za-z][A-Za-z0-9_]*)\\s*(?:\\([^)]*\\))?\\s*:");
    private static final Pattern SCHEMA_MAPPING = Pattern.compile(
            "@SchemaMapping\\s*\\(\\s*typeName\\s*=\\s*\"([^\"]+)\"\\s*,\\s*field\\s*=\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern ROOT_MAPPING = Pattern.compile(
            "@(QueryMapping|MutationMapping)(?:\\s*\\([^)]*\\))?(?:\\s+@[A-Za-z][A-Za-z0-9_.]*(?:\\s*\\([^)]*\\))?)*\\s+public\\s+[A-Za-z0-9_<>?,.\\[\\] ]+\\s+([A-Za-z][A-Za-z0-9_]*)\\s*\\(",
            Pattern.MULTILINE);

    @Test
    void catalogExactlyCoversSchemaBusinessFieldsAndControllerMappings() throws IOException {
        Set<String> schemaCoordinates = schemaCoordinates();
        Map<String, String> controllerMappings = controllerMappings();
        Map<String, Map<String, String>> catalogEntries = catalogEntries();

        assertThat(controllerMappings.keySet())
                .as("every schema business field must have an annotated controller mapping")
                .containsExactlyInAnyOrderElementsOf(schemaCoordinates);
        assertThat(catalogEntries.keySet())
                .as("GraphQL performance catalog must exactly match the registered business coordinates")
                .containsExactlyInAnyOrderElementsOf(controllerMappings.keySet());
        controllerMappings.forEach((coordinate, owner) -> assertThat(catalogEntries.get(coordinate).get("owner"))
                .as(coordinate + " must name its exact annotated controller owner")
                .isEqualTo(owner));
    }

    @Test
    void everyGraphQlEntryHasExplicitOwnerAuthRiskAndBudgets() throws IOException {
        Map<String, Map<String, String>> entries = catalogEntries();
        Set<String> violations = new LinkedHashSet<>();
        entries.forEach((coordinate, metadata) -> {
            Set<String> missing = new LinkedHashSet<>(REQUIRED_METADATA);
            missing.removeAll(metadata.keySet());
            if (!missing.isEmpty()) violations.add(coordinate + " missing " + missing);
            REQUIRED_METADATA.stream()
                    .filter(key -> metadata.getOrDefault(key, "").isBlank())
                    .forEach(key -> violations.add(coordinate + " has blank " + key));
        });
        assertThat(violations).isEmpty();
    }

    @Test
    void cachePolicyIsExplicitAndSafeForEveryGraphQlCoordinate() throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        catalogEntries().forEach((coordinate, metadata) -> {
            String cacheability = metadata.getOrDefault("cacheability", "");
            if (!Set.of("none", "tenant", "tenant-user-scope", "immutable-version").contains(cacheability)) {
                violations.add(coordinate + " has unsupported cacheability " + cacheability);
            }
            if (!"none".equals(cacheability)
                    && Integer.parseInt(metadata.getOrDefault("cache-ttl-seconds", "0")) <= 0) {
                violations.add(coordinate + " cache TTL must be positive");
            }
            if ("tenant-user-scope".equals(cacheability)
                    && !metadata.getOrDefault("cache-scope", "").contains("user")) {
                violations.add(coordinate + " user-scoped cache must include user identity");
            }
        });
        assertThat(violations).isEmpty();
    }

    private Set<String> schemaCoordinates() throws IOException {
        Set<String> coordinates = new LinkedHashSet<>();
        String currentType = null;
        for (String line : Files.readAllLines(SCHEMA)) {
            var typeMatcher = SCHEMA_TYPE.matcher(line);
            if (typeMatcher.find()) {
                currentType = BUSINESS_TYPES.contains(typeMatcher.group(1)) ? typeMatcher.group(1) : null;
                continue;
            }
            if (line.strip().equals("}")) {
                currentType = null;
                continue;
            }
            if (currentType == null) continue;
            var fieldMatcher = SCHEMA_FIELD.matcher(line);
            if (fieldMatcher.find() && !SHELL_FIELDS.contains(fieldMatcher.group(1))) {
                coordinates.add(currentType + "." + fieldMatcher.group(1));
            }
        }
        return coordinates;
    }

    private Map<String, String> controllerMappings() throws IOException {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (String source : controllerSources()) {
            String packageName = source.lines().filter(line -> line.startsWith("package "))
                    .findFirst().orElseThrow().substring("package ".length()).replace(";", "").trim();
            String className = Pattern.compile("public\\s+class\\s+([A-Za-z][A-Za-z0-9_]*)")
                    .matcher(source).results().findFirst().map(match -> match.group(1)).orElse(null);
            if (className == null) continue;
            String owner = packageName + "." + className;
            Pattern schemaOwner = Pattern.compile(
                    SCHEMA_MAPPING.pattern() + "[\\s\\S]*?public\\s+[A-Za-z0-9_<>?,.\\[\\] ]+\\s+([A-Za-z][A-Za-z0-9_]*)\\s*\\(");
            schemaOwner.matcher(source).results().forEach(match ->
                    mappings.put(match.group(1) + "." + match.group(2), owner + "." + match.group(3)));
            ROOT_MAPPING.matcher(source).results().forEach(match -> {
                String type = match.group(1).equals("QueryMapping") ? "Query" : "Mutation";
                mappings.put(type + "." + match.group(2), owner + "." + match.group(2));
            });
        }
        return mappings;
    }

    private Set<String> controllerSources() throws IOException {
        try (var files = Files.walk(Path.of("src/main/java/com/erg/ergspringboot"))) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .filter(source -> source.contains("@Controller"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private Map<String, Map<String, String>> catalogEntries() throws IOException {
        Map<String, Map<String, String>> entries = new LinkedHashMap<>();
        String coordinate = null;
        for (String rawLine : Files.readAllLines(CATALOG)) {
            String line = rawLine.strip();
            if (line.startsWith("- coordinate: ")) {
                coordinate = line.substring("- coordinate: ".length()).trim();
                entries.put(coordinate, new LinkedHashMap<>());
            } else if (coordinate != null && line.contains(":")) {
                String key = line.substring(0, line.indexOf(':')).trim();
                String value = line.substring(line.indexOf(':') + 1).trim();
                entries.get(coordinate).put(key, value);
            }
        }
        return entries;
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
