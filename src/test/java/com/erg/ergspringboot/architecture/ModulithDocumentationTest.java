package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.ErgSpringApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithDocumentationTest {
    @Test
    void everyExplicitModuleHasBoundaryAndDependencyDocumentation() throws Exception {
        String boundaries = Files.readString(Path.of("docs/architecture/spring-modulith-boundaries.md"));
        String matrix = Files.readString(Path.of("docs/architecture/module-dependency-matrix.md"));
        String catalog = Files.readString(Path.of("docs/architecture/module-catalog.md"));
        String graph = Files.readString(Path.of("docs/architecture/module-dependency-graph.md"));
        System.setProperty("spring.modulith.detection-strategy", "explicitly-annotated");
        ApplicationModules modules = ApplicationModules.of(ErgSpringApplication.class);
        new Documenter(modules).writeDocumentation();

        Path diagram = Path.of("target/spring-modulith-docs/module-calendar.puml");
        Path canvas = Path.of("target/spring-modulith-docs/module-calendar.adoc");

        assertThat(boundaries).contains("calendar", "at-least-once", "internal/");
        assertThat(matrix).contains("| calendar |", "CalendarOperations", "CalendarBatchPublished");
        assertThat(catalog).contains("| `calendar` |", "CalendarOperations", "CalendarBatchPublished");
        assertThat(graph).contains("ApplicationModules.verify()", "calendar.internal");
        assertThat(diagram).exists().isRegularFile();
        assertThat(canvas).exists().isRegularFile();
        assertThat(Files.readString(diagram)).containsIgnoringCase("calendar");
        assertThat(Files.readString(canvas)).contains("CalendarOperations", "CalendarBatchPublished");
    }
}
