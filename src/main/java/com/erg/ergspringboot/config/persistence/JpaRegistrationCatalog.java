package com.erg.ergspringboot.config.persistence;

import com.erg.ergspringboot.content.persistence.ContentLifecycleTransition;
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
    /** Repository scan root: horizontal {@code repository.*} plus module-owned {@code <module>.persistence}. */
    public static final String REPOSITORY_SCAN_ROOT = "com.erg.ergspringboot";

    private static final List<Module> MODULES = List.of(
            enabled("auth", 5, 5),
            owned("audit", 1, 1),
            enabled("authorization", 6, 6),
            owned("calendar", 5, 5),
            ownedPartiallyManaged("content", 4, 1, ContentLifecycleTransition.class.getName()),
            owned("crm", 6, 6),
            enabled("curriculum", 6, 6),
            owned("enterpriseconnectors", 3, 3),
            owned("lcms", 7, 7),
            owned("learningcontent", 4, 5),
            owned("learningoutcomes", 7, 7),
            owned("learningstandards", 4, 4),
            owned("lms", 12, 12),
            // Quiz-bank entities stay horizontal: lcms uses them and lms depends on lcms, so lms.persistence would form a cycle.
            enabled("lms", 4, 4),
            owned("notifications", 7, 7),
            enabled("organization", 3, 2),
            owned("outbox", "auth", 1, 1),
            enabled("quiz", 7, 7),
            owned("reporting", 1, 1),
            owned("school", 3, 4),
            owned("users", 5, 4)
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
            String repositoryPackage,
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
        return new Module(name, model(name), repository(name), true, entities, repositories, Set.of());
    }

    /** Persistence folded into another module, e.g. outbox lives in {@code auth.persistence}. */
    private static Module owned(String name, String owner, int entities, int repositories) {
        String persistence = "com.erg.ergspringboot." + owner + ".persistence";
        return new Module(name, persistence, persistence, true, entities, repositories, Set.of());
    }

    /** P3-4: entities and repositories live in the owning module at {@code <module>.persistence}. */
    private static Module owned(String name, int entities, int repositories) {
        String persistence = "com.erg.ergspringboot." + name + ".persistence";
        return new Module(name, persistence, persistence, true, entities, repositories, Set.of());
    }

    /** Module-owned package where only the allow-listed classes are managed; the rest stay dormant. */
    private static Module ownedPartiallyManaged(String name, int entities, int repositories, String... classes) {
        String persistence = "com.erg.ergspringboot." + name + ".persistence";
        return new Module(name, persistence, persistence, false, entities, repositories, Set.of(classes));
    }

    private static Module dormant(String name, int entities) {
        return new Module(name, model(name), repository(name), false, entities, 0, Set.of());
    }

    private static String model(String name) {
        return "com.erg.ergspringboot.model." + name;
    }

    private static String repository(String name) {
        return REPOSITORY_ROOT + "." + name;
    }
}
