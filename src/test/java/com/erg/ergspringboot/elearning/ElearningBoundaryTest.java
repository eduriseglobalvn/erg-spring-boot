package com.erg.ergspringboot.elearning;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.elearning.graphql.StudentDashboardGraphQlController;
import com.erg.ergspringboot.controller.elearning.graphql.StudentEngagementGraphQlController;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ElearningBoundaryTest {

    @Test
    void graphqlControllersDependOnlyOnThePublicFacade() {
        assertThat(StudentDashboardGraphQlController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(ElearningOperations.class,
                                com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport.class));
        assertThat(StudentEngagementGraphQlController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(ElearningOperations.class,
                                com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport.class));
    }

    @Test
    void publicElearningPackageDoesNotImportPersistenceTypes() throws Exception {
        Path root = Path.of("src/main/java/com/erg/ergspringboot/elearning");
        try (var files = Files.walk(root)) {
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
                            || source.contains("jakarta.persistence.")))
                    .isTrue();
        }
    }

    @Test
    void persistentServicesAreDeprecatedCompatibilityAdapters() throws Exception {
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/elearning/engagement/StudentEngagementService.java")))
                .contains("@Deprecated", "persistent");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/elearning/readmodel/StudentDashboardReadService.java")))
                .contains("@Deprecated", "persistent");
    }
}
