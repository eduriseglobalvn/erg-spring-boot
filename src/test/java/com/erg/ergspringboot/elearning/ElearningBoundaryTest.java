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
                        .containsExactly(ElearningOperations.class, StudentTierOperations.class, StudentQuestionThreadOperations.class,
                                com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport.class));
    }

    @Test
    void publicElearningPackageDoesNotImportPersistenceTypes() throws Exception {
        Path root = Path.of("src/main/java/com/erg/ergspringboot/elearning");
        try (var files = Files.walk(root)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !root.relativize(path).startsWith("internal"))
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
    void persistentServicesLiveInsideTheModuleAndNoHorizontalServiceRemains() throws Exception {
        // P3-4: the persistent services moved into elearning/internal; no horizontal service/elearning exists.
        assertThat(Path.of("src/main/java/com/erg/ergspringboot/service/elearning")).doesNotExist();
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/elearning/internal/engagement/StudentEngagementService.java")))
                .contains("persistent").doesNotContain("@Deprecated");
        assertThat(Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/elearning/internal/readmodel/StudentDashboardReadService.java")))
                .contains("persistent").doesNotContain("@Deprecated");
    }
}
