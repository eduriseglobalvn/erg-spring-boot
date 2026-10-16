package com.erg.ergspringboot.enterpriseconnectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EnterpriseConnectorBoundaryTest {
    @Test
    void publicModuleDoesNotImportPersistenceOrProviderSdkTypes() throws Exception {
        Path root = Path.of("src/main/java/com/erg/ergspringboot/enterpriseconnectors");
        try (var files = Files.list(root)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .noneMatch(source -> source.contains("com.erg.ergspringboot.model.")
                            || source.contains("com.erg.ergspringboot.repository.")
                            || source.contains("org.springframework.data.jpa")
                            || source.contains("com.fasterxml.jackson.databind")))
                    .isTrue();
        }
    }

    @Test
    void providerPortContainsOnlyBoundedScalarContracts() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/enterpriseconnectors/EnterpriseConnectorProviderPort.java"));
        assertThat(source).doesNotContain("HttpClient", "WebClient", "RestClient", "com.erg.ergspringboot.model.",
                "com.erg.ergspringboot.repository.");
    }
}
