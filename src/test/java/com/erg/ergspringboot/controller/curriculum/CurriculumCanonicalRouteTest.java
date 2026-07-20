package com.erg.ergspringboot.controller.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CurriculumCanonicalRouteTest {

    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/erg/ergspringboot/controller/curriculum/CurriculumController.java");
    private static final Path SECURITY = Path.of(
            "src/main/java/com/erg/ergspringboot/platform/security/SecurityConfig.java");
    private static final Path CATALOG = Path.of(
            "src/test/resources/performance/api-performance-catalog.yaml");

    @Test
    void curriculumRestSurfaceUsesVersionedRouteOnly() throws IOException {
        String controller = Files.readString(CONTROLLER);
        String security = Files.readString(SECURITY);
        String catalog = Files.readString(CATALOG);

        assertThat(controller)
                .contains("@RequestMapping(\"/api/v1/curriculum\")")
                .doesNotContain("@RequestMapping(\"/api/curriculum\")");
        assertThat(security).contains("/api/v1/curriculum/**");
        assertThat(catalog)
                .contains("GET /api/v1/curriculum/tree =>")
                .doesNotContain("GET /api/curriculum/tree =>");
    }
}
