package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.ErgSpringApplication;
import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureRatchetTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/erg/ergspringboot");
    private static final Path INTERNAL_BASELINE =
            Path.of("src/test/resources/architecture/internal-access-baseline.tsv");
    private static final Path HORIZONTAL_BASELINE =
            Path.of("src/test/resources/architecture/horizontal-package-baseline.properties");
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+([a-zA-Z0-9_.*]+);");
    private static final Pattern CATALOG_MODULE = Pattern.compile("(?m)^\\| `([a-z][a-z0-9-]*)` \\|");
    private static final Set<String> HORIZONTAL_PACKAGES =
            Set.of("controller", "service", "repository", "model", "common");

    @BeforeAll
    static void useExplicitModuleDetection() {
        System.setProperty("spring.modulith.detection-strategy", "explicitly-annotated");
    }

    @Test
    void baseEntityStableCoreContainsOnlyIdentityAndLifecycleTimestamps() throws Exception {
        Set<String> fields = Arrays.stream(BaseEntity.class.getDeclaredFields())
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        assertThat(fields).containsExactly("createdAt", "id", "updatedAt");

        Method onCreate = BaseEntity.class.getDeclaredMethod("onCreate");
        Method onUpdate = BaseEntity.class.getDeclaredMethod("onUpdate");
        assertThat(onCreate.isAnnotationPresent(PrePersist.class)).isTrue();
        assertThat(onUpdate.isAnnotationPresent(PreUpdate.class)).isTrue();
    }

    @Test
    void externalImportsOfModuleInternalsCannotGrowBeyondReviewedBaseline() throws IOException {
        Set<String> actual = discoverExternalInternalImports();
        Map<String, BaselineEntry> baseline = readInternalAccessBaseline();

        assertThat(baseline.values())
                .allSatisfy(entry -> {
                    assertThat(entry.owner()).isNotBlank();
                    assertThat(entry.expiresOn()).isAfterOrEqualTo(LocalDate.now());
                });
        assertThat(actual).isSubsetOf(baseline.keySet());
    }

    @Test
    void horizontalLegacyPackagesCannotGainProductionTypes() throws IOException {
        Map<String, Integer> baseline = readHorizontalBaseline();
        assertThat(baseline.keySet()).containsExactlyInAnyOrderElementsOf(HORIZONTAL_PACKAGES);

        for (String packageName : HORIZONTAL_PACKAGES) {
            long actual = javaFiles(SOURCE_ROOT.resolve(packageName)).count();
            assertThat(actual)
                    .as("%s must migrate inward instead of growing", packageName)
                    .isLessThanOrEqualTo(baseline.get(packageName));
        }
    }

    @Test
    void onlyJpaEntitiesMayInheritMappedBaseEntity() throws IOException {
        List<Path> violations = new ArrayList<>();
        try (Stream<Path> files = javaFiles(SOURCE_ROOT.resolve("model"))) {
            files.forEach(path -> {
                try {
                    String source = Files.readString(path);
                    if (source.contains("extends BaseEntity")
                            && !source.matches("(?s).*@(?:jakarta\\.persistence\\.)?Entity\\b.*")) {
                        violations.add(path);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void checkedInModuleCatalogMatchesDiscoveredApplicationModules() throws IOException {
        Set<String> discovered = new TreeSet<>();
        ApplicationModules.of(ErgSpringApplication.class)
                .forEach(module -> discovered.add(module.getIdentifier().toString()));

        String catalog = Files.readString(Path.of("docs/architecture/module-catalog.md"));
        Set<String> documented = new TreeSet<>();
        Matcher matcher = CATALOG_MODULE.matcher(catalog);
        while (matcher.find()) documented.add(matcher.group(1));

        assertThat(documented).containsExactlyElementsOf(discovered);
    }

    @Test
    void processLocalPrototypeServicesRemainUnavailableOutsidePrototypeProfile() throws IOException {
        List<Path> unsafe = new ArrayList<>();
        try (Stream<Path> files = javaFiles(SOURCE_ROOT.resolve("service"))) {
            files.forEach(path -> {
                try {
                    String source = Files.readString(path);
                    if (source.contains("ConcurrentHashMap")
                            && source.contains("@Service")
                            && !source.contains("Profile(\"prototype\")")) {
                        unsafe.add(path);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        assertThat(unsafe).isEmpty();
    }

    private Set<String> discoverExternalInternalImports() throws IOException {
        Set<String> moduleNames = new LinkedHashSet<>();
        try (Stream<Path> packageDescriptors = Files.walk(SOURCE_ROOT, 2)) {
            packageDescriptors
                    .filter(path -> path.getFileName().toString().equals("package-info.java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("@org.springframework.modulith.ApplicationModule");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .forEach(path -> moduleNames.add(path.getParent().getFileName().toString()));
        }

        Set<String> violations = new TreeSet<>();
        try (Stream<Path> files = javaFiles(SOURCE_ROOT)) {
            files.forEach(path -> {
                try {
                    String source = Files.readString(path);
                    String sourcePackage = sourcePackage(source);
                    Matcher matcher = IMPORT.matcher(source);
                    while (matcher.find()) {
                        String imported = matcher.group(1);
                        for (String module : moduleNames) {
                            String modulePackage = "com.erg.ergspringboot." + module;
                            if (imported.startsWith(modulePackage + ".internal.")
                                    && !sourcePackage.equals(modulePackage)
                                    && !sourcePackage.startsWith(modulePackage + ".")) {
                                violations.add(relative(path) + "\t" + imported);
                            }
                        }
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        return violations;
    }

    private Map<String, BaselineEntry> readInternalAccessBaseline() throws IOException {
        Map<String, BaselineEntry> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(INTERNAL_BASELINE)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] columns = line.split("\\t", -1);
            assertThat(columns).as("baseline row: %s", line).hasSize(4);
            String key = columns[0] + "\t" + columns[1];
            assertThat(entries).doesNotContainKey(key);
            entries.put(key, new BaselineEntry(columns[2], LocalDate.parse(columns[3])));
        }
        return entries;
    }

    private Map<String, Integer> readHorizontalBaseline() throws IOException {
        Map<String, Integer> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(HORIZONTAL_BASELINE)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] columns = line.split("=", 2);
            assertThat(columns).as("baseline row: %s", line).hasSize(2);
            entries.put(columns[0].trim(), Integer.parseInt(columns[1].trim()));
        }
        return entries;
    }

    private Stream<Path> javaFiles(Path root) throws IOException {
        if (!Files.exists(root)) return Stream.empty();
        return Files.walk(root).filter(path -> path.toString().endsWith(".java"));
    }

    private String sourcePackage(String source) {
        int start = source.indexOf("package ");
        int end = source.indexOf(';', start);
        return start < 0 || end < 0 ? "" : source.substring(start + 8, end).trim();
    }

    private String relative(Path path) {
        return SOURCE_ROOT.relativize(path).toString().replace('\\', '/');
    }

    private record BaselineEntry(String owner, LocalDate expiresOn) {}
}
