package com.erg.ergspringboot.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CurriculumPublicFacadeArchitectureTest {

    @Test
    void controllerUsesTheCurriculumModulePublicFacade() throws Exception {
        assertThatCode(() -> Class.forName("com.erg.ergspringboot.curriculum.CurriculumOperations"))
                .doesNotThrowAnyException();
        String controller = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/curriculum/CurriculumController.java"));
        assertThat(controller)
                .contains("import com.erg.ergspringboot.curriculum.CurriculumOperations;")
                .doesNotContain("import com.erg.ergspringboot.service.curriculum.CurriculumService;");
    }
}
