package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.CalendarReadCompatibilityPort;
import com.erg.ergspringboot.calendar.CalendarReferenceMaps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fail-closed ratchets for module public packages.
 *
 * <p>Spring Modulith correctly protects explicit module-to-module edges, but
 * horizontal legacy packages sit outside that graph. This test prevents the
 * LCMS public contract from becoming a new dumping ground while its two
 * explicitly deprecated construction adapters are retired.</p>
 */
class ModulePublicPackageDirectionTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/erg/ergspringboot");
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+([a-zA-Z0-9_.*]+);");
    private static final Set<String> APPROVED_LCMS_ADAPTERS = Set.of(
            "LcmsCommandCompatibility.java",
            "LcmsReadCompatibility.java");

    @Test
    void lcmsPublicPackageCannotGrowLegacyOrPersistenceImportsOutsideAdapters() throws IOException {
        Path lcmsRoot = SOURCE_ROOT.resolve("lcms");
        List<String> violations = new java.util.ArrayList<>();
        try (Stream<Path> files = Files.list(lcmsRoot)) {
            files.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !APPROVED_LCMS_ADAPTERS.contains(path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            Matcher matcher = IMPORT.matcher(Files.readString(path));
                            while (matcher.find()) {
                                String imported = matcher.group(1);
                                if (isLegacyOrPersistenceImport(imported)) {
                                    violations.add(path.getFileName() + " -> " + imported);
                                }
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }

        assertThat(violations).as("LCMS public package must depend on module-owned contracts")
                .isEmpty();
    }

    @Test
    void approvedLcmsAdaptersRemainDeprecatedAndExplicit() throws IOException {
        for (String file : APPROVED_LCMS_ADAPTERS) {
            String source = Files.readString(SOURCE_ROOT.resolve("lcms").resolve(file));
            assertThat(source).as(file).contains("@Deprecated", "Compatibility");
        }
    }

    @Test
    void calendarCanonicalPortDoesNotExposePersistenceProjection() {
        assertThat(Stream.of(CalendarReadPort.class.getDeclaredMethods())
                .flatMap(method -> Stream.of(method.getParameterTypes()))
                .map(Class::getName)
                .noneMatch(name -> name.startsWith("com.erg.ergspringboot.model.")))
                .as("CalendarReadPort must expose only module-owned contracts")
                .isTrue();
        assertThat(CalendarReadCompatibilityPort.class.isAnnotationPresent(Deprecated.class))
                .as("entity projection must be isolated behind a deprecated compatibility port")
                .isTrue();
        assertThat(CalendarReferenceMaps.class.isAnnotationPresent(Deprecated.class))
                .as("CalendarReferenceMaps must not become a new public persistence contract")
                .isTrue();
    }

    private boolean isLegacyOrPersistenceImport(String imported) {
        return imported.startsWith("com.erg.ergspringboot.service.")
                || imported.startsWith("com.erg.ergspringboot.repository.")
                || imported.startsWith("com.erg.ergspringboot.model.")
                || imported.startsWith("jakarta.persistence.");
    }
}
