package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModulithDocumentationTest {
    @Test
    void everyExplicitModuleHasBoundaryAndDependencyDocumentation() throws Exception {
        String boundaries = Files.readString(Path.of("docs/architecture/spring-modulith-boundaries.md"));
        String matrix = Files.readString(Path.of("docs/architecture/module-dependency-matrix.md"));
        String generated = Files.readString(Path.of("docs/architecture/generated-modules.md"));

        assertThat(boundaries).contains("calendar", "at-least-once", "internal/");
        assertThat(matrix).contains("| calendar |", "CalendarOperations", "CalendarBatchPublished");
        assertThat(generated).contains("calendar", "CalendarBatchPublishedListener");
    }
}
