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
        assertThat(Files.exists(Path.of("ops/native/README.md"))).isTrue();
    }

    @Test
    void nativeRuntimeUsesTheExternalMigrationDirectoryCopiedIntoTheImage() throws Exception {
        String compose = Files.readString(Path.of("ops/docker/compose.native.yml"));
        assertThat(compose).contains("FLYWAY_LOCATIONS: filesystem:/app/db/migration/postgres");
    }
}
