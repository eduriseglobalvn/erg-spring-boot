package com.erg.ergspringboot.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RuntimePermissionCatalogContractTest {

    private static final Pattern KEY = Pattern.compile("[\\\"']([a-z][a-z0-9_-]*(?:\\.[a-z0-9_*_-]+)+)[\\\"']");
    private static final Path JAVA = Path.of("src/main/java/com/erg/ergspringboot");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/postgres");

    @Test
    void everyRuntimePermissionIsSeededAndRegisteredAsAnEnforcedBinding() throws Exception {
        Set<String> runtime = runtimePermissions();
        Set<String> seeded = valuesFromFiles(MIGRATIONS, ".sql");
        String catalogMigrations;
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            catalogMigrations = files
                    .filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .map(RuntimePermissionCatalogContractTest::readUnchecked)
                    .collect(java.util.stream.Collectors.joining("\n"));
        }

        assertThat(seeded).containsAll(runtime);
        assertThat(runtime)
                .allSatisfy(permission -> assertThat(catalogMigrations).contains("'" + permission + "'"));
    }

    private Set<String> runtimePermissions() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(valuesFromFile(JAVA.resolve("platform/security/SecurityConfig.java")));
        keys.addAll(valuesFromFiles(JAVA.resolve("controller"), ".java"));
        keys.addAll(valuesFromFiles(JAVA.resolve("platform/graphql"), ".java"));
        keys.removeIf(key -> key.startsWith("api.")
                || key.startsWith("spring.")
                || key.startsWith("graphql.")
                || key.startsWith("app.")
                || key.startsWith("erg."));
        return keys;
    }

    private Set<String> valuesFromFiles(Path root, String suffix) throws IOException {
        Set<String> values = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(suffix)).toList()) {
                values.addAll(valuesFromFile(file));
            }
        }
        return values;
    }

    private Set<String> valuesFromFile(Path file) throws IOException {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = KEY.matcher(Files.readString(file));
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }
}
