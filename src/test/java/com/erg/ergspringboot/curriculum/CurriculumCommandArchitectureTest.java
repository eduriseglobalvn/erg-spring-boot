package com.erg.ergspringboot.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CurriculumCommandArchitectureTest {

    @Test
    void taxonomyMutationsHaveAnInternalTransactionalOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.curriculum.internal.command.CurriculumCommandUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/curriculum/CurriculumService.java"));
        assertThat(facade)
                .contains("commands.createSubject(")
                .contains("commands.hardDeleteLevel(")
                .contains("commands.archiveTopic(")
                .doesNotContain("applyNodeUpdate(");
    }
}
