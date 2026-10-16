package com.erg.ergspringboot.learningcontent;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.controller.learningcontent.LearningContentController;
import com.erg.ergspringboot.learningcontent.api.LearningContentRequests.CreateContentItemRequest;
import com.erg.ergspringboot.learningcontent.api.LearningContentResponses.ContentItemResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LearningContentBoundaryTest {

    @Test
    void controllerDependsOnModuleFacadeAndUsesModuleOwnedResponse() throws Exception {
        assertThat(LearningContentController.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(LearningContentOperations.class));
        assertThat(LearningContentController.class
                .getMethod("createContentItem", 
                        CreateContentItemRequest.class,
                        jakarta.servlet.http.HttpServletRequest.class)
                .getGenericReturnType().getTypeName())
                .contains(ContentItemResponse.class.getSimpleName());
    }

    @Test
    void publicLearningContentTypesDoNotImportPersistence() throws Exception {
        Path root = Path.of("src/main/java/com/erg/ergspringboot/learningcontent");
        try (var files = Files.walk(root)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .filter(source -> source.contains("package com.erg.ergspringboot.learningcontent;"))
                    .noneMatch(source -> source.contains("com.erg.ergspringboot.model.")
                            || source.contains("com.erg.ergspringboot.repository.")
                            || source.contains("com.erg.ergspringboot.service.learningcontent.LearningContentRequests")
                            || source.contains("jakarta.persistence.")))
                    .isTrue();
        }
    }
}
