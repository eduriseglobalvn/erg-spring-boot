package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostgresJpaCanonicalPackageContractTest {

    @Test
    void canonicalCurriculumRepositoriesAndEntitiesAreRegistered() throws IOException {
        String config = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/config/persistence/PostgresJpaConfig.java"
        ));

        assertThat(config)
                .contains("com.erg.ergspringboot.repository.curriculum")
                .contains("com.erg.ergspringboot.model.curriculum")
                .contains("com.erg.ergspringboot.repository.learningcontent")
                .contains("com.erg.ergspringboot.model.learningcontent");
    }
}
