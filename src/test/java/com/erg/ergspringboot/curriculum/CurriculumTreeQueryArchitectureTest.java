package com.erg.ergspringboot.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;

class CurriculumTreeQueryArchitectureTest {

    @Test
    void curriculumIsAnExplicitModuleAndTreeCompositionHasAnInternalOwner() throws Exception {
        assertThat(CurriculumTreeQueryArchitectureTest.class.getPackage()
                .getAnnotation(ApplicationModule.class)).isNotNull();
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.curriculum.internal.query.CurriculumTreeQueryUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/curriculum/CurriculumService.java"));
        assertThat(facade).contains("treeQueries.tree(");
    }
}
