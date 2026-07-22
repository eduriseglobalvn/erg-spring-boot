package com.erg.ergspringboot.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CurriculumImpactQueryArchitectureTest {

    @Test
    void dependencyImpactQueriesHaveAnInternalOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.curriculum.internal.impact.CurriculumImpactQueryUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/curriculum/CurriculumService.java"));
        assertThat(facade)
                .contains("impactQueries.subjectImpact(")
                .contains("impactQueries.levelImpact(")
                .contains("impactQueries.topicImpact(");
    }
}
