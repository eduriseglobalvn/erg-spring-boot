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

    /** Repository scan root; every repository lives in its module at {@code <module>.persistence}. */
    public static final String REPOSITORY_SCAN_ROOT = "com.erg.ergspringboot";

    private static final List<Module> MODULES = List.of(
            owned("audit", 1, 1),
            owned("calendar", 5, 5),
            ownedPartiallyManaged("content", 4, 1, ContentLifecycleTransition.class.getName()),
            owned("crm", 6, 6),
            owned("curriculum", 7, 7),
            owned("enterpriseconnectors", 3, 3),
            owned("lcms", 18, 18),
            owned("learningcontent", 4, 5),
            owned("learningoutcomes", 7, 7),
            owned("learningstandards", 4, 4),
            owned("lms", 12, 15),
            owned("notifications", 8, 8),
            owned("outbox", "auth", 1, 1),
            owned("reporting", 1, 1),
            owned("school", 3, 4),
            owned("timekeeping", 14, 14),
            owned("users", 19, 17)
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
}
