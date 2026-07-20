package com.erg.ergspringboot.platform.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NativeImageBuildContractTest {

    @Test
    void nativeBuildPluginAndSmokeRunbookArePresent() throws Exception {
        assertThat(Files.exists(Path.of("pom.xml"))).isTrue();
        assertThat(Files.readString(Path.of("pom.xml")))
                .contains("org.graalvm.buildtools", "native-maven-plugin");
        assertThat(Files.readString(Path.of("pom.xml")))
                .contains("<id>native</id>", "<imageName>erg-spring-native</imageName>")
                .contains("<goal>process-aot</goal>", "<mainClass>com.erg.ergspringboot.ErgSpringApplication</mainClass>");
        assertThat(Files.readString(Path.of("ops/native/README.md")))
                .contains("SPRING_PROFILES_ACTIVE=prod", "FLYWAY_LOCATIONS=filesystem:/app/db/migration/postgres")
                .contains("-Pnative -DskipTests native:compile")
                .contains("-f ops/docker/compose.yml", "-f ops/docker/compose.native.yml up -d")
                .doesNotContain("native:compile-no-fork");
        assertThat(Files.exists(Path.of("ops/native/README.md"))).isTrue();
        assertThat(Files.readString(Path.of("Dockerfile")))
                .contains("org.springframework.modulith.observability.autoconfigure.ModuleObservabilityAutoConfiguration");
    }

    @Test
    void nativeRuntimeUsesTheExternalMigrationDirectoryCopiedIntoTheImage() throws Exception {
        String compose = Files.readString(Path.of("ops/docker/compose.native.yml"));
        assertThat(compose)
                .contains("FLYWAY_LOCATIONS: filesystem:/app/db/migration/postgres")
                .contains("SPRING_MODULITH_RUNTIME_VERIFICATION_ENABLED: \"false\"")
                .contains("org.springframework.modulith.observability.autoconfigure.ModuleObservabilityAutoConfiguration");
    }

    @Test
    void nativeRuntimeRegistersTheArchUnitPluginLoadedByModulithInsight() throws Exception {
        String application = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/ErgSpringApplication.java"));
        String hints = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/platform/nativeimage/ModulithNativeRuntimeHints.java"));

        assertThat(application).contains("@ImportRuntimeHints(ModulithNativeRuntimeHints.class)");
        assertThat(hints).contains("com.tngtech.archunit.core.importer.ModuleImportPlugin", "INVOKE_PUBLIC_CONSTRUCTORS");
    }
}
