package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionConfigurationSafetyTest {

    @Test
    void commonConfigurationFailsSafeForProduction() throws Exception {
        String common = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(common)
                .doesNotContain("active: ${SPRING_PROFILES_ACTIVE:dev}")
                .contains("enabled: ${FLYWAY_ENABLED:true}")
                .contains("include-message: never")
                .contains("include-stacktrace: never")
                .contains("include-binding-errors: never")
                .contains("enabled: ${SPRINGDOC_API_DOCS_ENABLED:false}")
                .contains("enabled: ${SCALAR_ENABLED:false}")
                .contains("enabled: ${ERG_PROCESS_LOCAL_ENABLED:false}");
    }

    @Test
    void localConvenienceIsExplicitlyOptIn() throws Exception {
        String local = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertThat(local)
                .contains("generate_statistics: true")
                .contains("include-message: always")
                .contains("api-docs:")
                .contains("process-local:");
    }
}
