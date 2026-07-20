package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SchoolJpaRegistrationTest {

    @Test
    void postgresPersistenceRegistersSchoolRepositoriesAndEntities() throws IOException {
        String config = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/config/persistence/PostgresJpaConfig.java"));

        assertThat(config)
                .contains("com.erg.ergspringboot.repository.school")
                .contains("com.erg.ergspringboot.model.school");
    }
}
