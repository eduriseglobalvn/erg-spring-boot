package com.erg.ergspringboot.platform.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ModuleNativeRuntimeHintsTest {
    @Test
    void migratedBusinessModulesDoNotUseRuntimeClassLoading() throws Exception {
        for (String module : new String[] {"users", "authorization", "lms", "calendar"}) {
            Path root = Path.of("src/main/java/com/erg/ergspringboot", module);
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    assertThat(Files.readString(file))
                            .as("native-safe module source %s", file)
                            .doesNotContain("Class.forName(")
                            .doesNotContain("Proxy.newProxyInstance(")
                            .doesNotContain("new URLClassLoader(");
                }
            }
        }
    }

    @Test
    void modulithArchUnitPluginHasAnExplicitNativeHint() throws Exception {
        String hints = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/nativeimage/ModulithNativeRuntimeHints.java"));
        assertThat(hints)
                .contains("com.tngtech.archunit.core.importer.ModuleImportPlugin")
                .contains("INVOKE_PUBLIC_CONSTRUCTORS");
    }
}
