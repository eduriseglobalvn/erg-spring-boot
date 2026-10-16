package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class JpaRegistrationCatalogTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/erg/ergspringboot");

    @Test
    void everyEntityPackageIsExplicitlyEnabledOrExplicitlyDormant() throws IOException {
        Map<String, Long> entityCounts = countByModule(
                SOURCE_ROOT.resolve("model"), source -> source.contains("@Entity"));
        Map<String, Long> repositoryCounts = countByModule(
                SOURCE_ROOT.resolve("repository"), source -> source.matches("(?s).*interface\\s+\\w+Repository\\b.*"));

        for (JpaRegistrationCatalog.Module module : JpaRegistrationCatalog.modules()) {
            assertThat(entityCounts.getOrDefault(module.name(), 0L).intValue())
                    .as("entity inventory for %s", module.name())
                    .isEqualTo(module.entityCount());
            assertThat(repositoryCounts.getOrDefault(module.name(), 0L).intValue())
                    .as("repository inventory for %s", module.name())
                    .isEqualTo(module.repositoryCount());
        }

        assertThat(entityCounts.keySet())
                .as("new entity package must be added to the catalog")
                .containsExactlyInAnyOrderElementsOf(
                        JpaRegistrationCatalog.modules().stream().map(JpaRegistrationCatalog.Module::name).toList());
        assertThat(repositoryCounts.keySet())
                .as("new repository package must be added to the catalog")
                .containsExactlyInAnyOrderElementsOf(
                        JpaRegistrationCatalog.modules().stream()
                                .filter(module -> module.repositoryCount() > 0)
                                .map(JpaRegistrationCatalog.Module::name)
                                .toList());
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
    void repositoryRegistrationUsesCanonicalRootConvention() throws IOException {
        String config = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/config/persistence/PostgresJpaConfig.java"));
        assertThat(config)
                .contains("basePackages = JpaRegistrationCatalog.REPOSITORY_ROOT")
                .contains("JpaRegistrationCatalog.entityScanPackages()")
                .contains("JpaRegistrationCatalog::isManagedEntityClass");
    }

    private Map<String, Long> countByModule(Path root, Predicate<String> include) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Map.entry(root.relativize(path).getName(0).toString(), Files.readString(path));
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .filter(entry -> include.test(entry.getValue()))
                    .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.counting()));
        }
    }
}
