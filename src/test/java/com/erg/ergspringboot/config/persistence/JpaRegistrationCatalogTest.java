package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class JpaRegistrationCatalogTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/erg/ergspringboot");

    @Test
    void everyEntityPackageIsExplicitlyEnabledOrExplicitlyDormant() throws IOException {
        for (JpaRegistrationCatalog.Module module : JpaRegistrationCatalog.modules()) {
            assertThat(count(module.modelPackage(), ENTITY))
                    .as("entity inventory for %s", module.name())
                    .isEqualTo(module.entityCount());
            assertThat(count(module.repositoryPackage(), REPOSITORY))
                    .as("repository inventory for %s", module.name())
                    .isEqualTo(module.repositoryCount());
        }

        // Every horizontal sub-package and every module-owned persistence package must be cataloged.
        var catalogedModels = JpaRegistrationCatalog.modules().stream()
                .map(JpaRegistrationCatalog.Module::modelPackage).toList();
        var catalogedRepositories = JpaRegistrationCatalog.modules().stream()
                .filter(module -> module.repositoryCount() > 0)
                .map(JpaRegistrationCatalog.Module::repositoryPackage).toList();
        // "@Entity" followed by whitespace/paren, so repositories using @EntityGraph do not count.
        assertThat(packagesContaining(source -> source.matches("(?s).*@Entity[\\s(].*")))
                .as("new entity package must be added to the catalog")
                .containsExactlyInAnyOrderElementsOf(catalogedModels);
        assertThat(packagesContaining(REPOSITORY))
                .as("new repository package must be added to the catalog")
                .containsExactlyInAnyOrderElementsOf(catalogedRepositories);
    }

    @Test
    void managedTypesUseOnlyEnabledPackagesAndKeepDormantEntitiesOut() throws IOException {
        var managedTypes = new PostgresJpaConfig().postgresManagedTypes(new DefaultResourceLoader());
        for (JpaRegistrationCatalog.Module module : JpaRegistrationCatalog.modules()) {
            String prefix = module.modelPackage() + ".";
            boolean hasEntity = managedTypes.getManagedClassNames().stream().anyMatch(name -> name.startsWith(prefix));
            boolean expectsEntity = module.enabled() || !module.explicitlyManagedEntityClasses().isEmpty();
            assertThat(hasEntity)
                    .as("managed-type registration for %s", module.name())
                    .isEqualTo(expectsEntity);
            assertThat(managedTypes.getManagedClassNames())
                    .as("explicit managed types for %s", module.name())
                    .containsAll(module.explicitlyManagedEntityClasses());
        }
        assertThat(managedTypes.getManagedClassNames())
                .noneMatch(name -> JpaRegistrationCatalog.dormantEntityPackages().stream()
                        .anyMatch(pkg -> name.startsWith(pkg + ".")));
    }

    @Test
    void ownedPartiallyManagedModuleAdmitsOnlyAllowListedEntities() {
        String persistence = "com.erg.ergspringboot.content.persistence";
        assertThat(JpaRegistrationCatalog.entityScanPackages()).contains(persistence);
        assertThat(JpaRegistrationCatalog.enabledEntityPackages()).doesNotContain(persistence);
        assertThat(JpaRegistrationCatalog.isManagedEntityClass(persistence + ".ContentLifecycleTransition")).isTrue();
        assertThat(JpaRegistrationCatalog.isManagedEntityClass(persistence + ".Post")).isFalse();
        assertThat(JpaRegistrationCatalog.isManagedEntityClass(persistence + ".Page")).isFalse();
    }

    @Test
    void repositoryRegistrationUsesCanonicalRootConvention() throws IOException {
        String config = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/config/persistence/PostgresJpaConfig.java"));
        assertThat(config)
                .contains("basePackages = JpaRegistrationCatalog.REPOSITORY_SCAN_ROOT")
                .contains("JpaRegistrationCatalog.entityScanPackages()")
                .contains("JpaRegistrationCatalog::isManagedEntityClass");
    }

    // "@Entity" followed by whitespace/paren, so co-located repositories using @EntityGraph do not count.
    private static final Predicate<String> ENTITY = source -> source.matches("(?s).*@Entity[\\s(].*");

    private static final Predicate<String> REPOSITORY =
            source -> source.matches("(?s).*interface\\s+\\w+Repository\\b.*");

    private int count(String javaPackage, Predicate<String> include) throws IOException {
        Path dir = Path.of("src/main/java", javaPackage.split("\\."));
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> files = Files.walk(dir)) {
            return (int) files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> include.test(read(path))).count();
        }
    }

    /**
     * Catalog-level packages that hold matching sources: {@code model.<x>} / {@code repository.<x>}
     * for horizontal modules and {@code <module>.persistence} for module-owned ones.
     */
    private java.util.Set<String> packagesContaining(Predicate<String> include) throws IOException {
        Path base = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> include.test(read(path)))
                    .map(path -> catalogPackage(base.relativize(path.getParent()).toString().replace(java.io.File.separatorChar, '.')))
                    .collect(Collectors.toSet());
        }
    }

    private static String catalogPackage(String javaPackage) {
        String root = "com.erg.ergspringboot.";
        String[] parts = javaPackage.substring(root.length()).split("\\.");
        if (parts[0].equals("model") || parts[0].equals("repository")) return root + parts[0] + "." + parts[1];
        return root + parts[0] + ".persistence";
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
