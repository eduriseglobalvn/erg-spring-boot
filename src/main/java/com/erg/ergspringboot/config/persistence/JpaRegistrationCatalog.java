package com.erg.ergspringboot.config.persistence;

import com.erg.ergspringboot.model.content.ContentLifecycleTransition;
import java.util.List;
import java.util.Set;

/**
 * Canonical persistence inventory. Enabled model packages are the only packages
 * passed to the managed-type scanner; repositories follow one root convention and
 * are guarded by the inventory test below. Dormant packages remain visible here so
 * they cannot be enabled accidentally by a broad entity scan.
 */
public final class JpaRegistrationCatalog {

    public static final String REPOSITORY_ROOT = "com.erg.ergspringboot.repository";

    private static final List<Module> MODULES = List.of(
            enabled("auth", 5, 6),
            enabled("audit", 1, 1),
            enabled("authorization", 6, 6),
            enabled("calendar", 5, 5),
            dormant("community", 6),
            dormant("configuration", 1),
            partiallyManaged("content", 4, 1, ContentLifecycleTransition.class.getName()),
            dormant("courses", 1),
            enabled("crm", 6, 6),
            enabled("curriculum", 3, 3),
            enabled("enterpriseconnectors", 3, 3),
            enabled("learningcontent", 4, 5),
            enabled("learningoutcomes", 7, 7),
            enabled("learningstandards", 4, 4),
            enabled("lms", 16, 16),
            enabled("notifications", 7, 7),
            enabled("organization", 3, 2),
            enabled("outbox", 1, 1),
            enabled("quiz", 14, 14),
            dormant("recruitment", 2),
            enabled("reporting", 1, 1),
            enabled("school", 3, 4),
            dormant("security", 1),
            enabled("users", 5, 3)
    );

    private JpaRegistrationCatalog() {
    }

    public static List<Module> modules() {
        return MODULES;
    }

    public static List<String> enabledEntityPackages() {
        return MODULES.stream()
                .filter(Module::enabled)
                .map(Module::modelPackage)
                .toList();
    }

    /**
     * Packages that must be scanned. A dormant package can still expose a
     * single explicitly managed entity while its legacy siblings remain out
     * of the persistence unit.
     */
    public static List<String> entityScanPackages() {
        return MODULES.stream()
                .filter(module -> module.enabled() || !module.explicitlyManagedEntityClasses().isEmpty())
                .map(Module::modelPackage)
                .toList();
    }

    /**
     * Explicit managed-type boundary used by the JPA scanner. Enabled modules
     * admit all entities in their package; partially managed modules admit
     * only their allow-listed classes.
     */
    public static boolean isManagedEntityClass(String className) {
        return MODULES.stream().anyMatch(module -> module.manages(className));
    }

    public static Set<String> dormantEntityPackages() {
        return MODULES.stream()
                .filter(module -> !module.enabled() && module.explicitlyManagedEntityClasses().isEmpty())
                .map(Module::modelPackage)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public record Module(
            String name,
            String modelPackage,
            boolean enabled,
            int entityCount,
            int repositoryCount,
            Set<String> explicitlyManagedEntityClasses
    ) {
        public Module {
            explicitlyManagedEntityClasses = Set.copyOf(explicitlyManagedEntityClasses);
        }

        private boolean manages(String className) {
            return explicitlyManagedEntityClasses.contains(className)
                    || (enabled && className.startsWith(modelPackage + "."));
        }
    }

    private static Module enabled(String name, int entities, int repositories) {
        return new Module(name, "com.erg.ergspringboot.model." + name, true, entities, repositories, Set.of());
    }

    private static Module dormant(String name, int entities) {
        return new Module(name, "com.erg.ergspringboot.model." + name, false, entities, 0, Set.of());
    }

    private static Module partiallyManaged(String name, int entities, int repositories, String... classes) {
        return new Module(name, "com.erg.ergspringboot.model." + name, false, entities, repositories, Set.of(classes));
    }
}
