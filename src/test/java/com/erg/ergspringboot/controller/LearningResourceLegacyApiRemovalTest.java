package com.erg.ergspringboot.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LearningResourceLegacyApiRemovalTest {

    @Test
    void legacyLearningResourceControllersAreNotMounted() throws IOException {
        String mainJava = allMainJava();

        assertThat(mainJava)
                .doesNotContain("/api/hoclieu")
                .doesNotContain("/api/v1/hoclieu")
                .doesNotContain("/api/admin/hoclieu")
                .doesNotContain("HocLieuController")
                .doesNotContain("HocLieuService")
                .doesNotContain("HocLieuResourceService");
    }

    @Test
    void canonicalCurriculumAndContentRestSurfaceIsCommandOnlyForLearningResourceReads() throws IOException {
        String curriculumController = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/curriculum/CurriculumController.java"
        ));
        String learningContentController = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/learningcontent/LearningContentController.java"
        ));

        assertThat(curriculumController)
                .doesNotContain("@GetMapping(\"/tree\")")
                .doesNotContain("@GetMapping(\"/subjects\")")
                .doesNotContain("@GetMapping(\"/levels\")")
                .doesNotContain("@GetMapping(\"/topics\")");
        assertThat(learningContentController)
                .doesNotContain("@GetMapping");
    }

    @Test
    void lmsControllerDoesNotReintroducePlaceholderLearningResourceReads() throws IOException {
        String lmsController = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/lms/LmsController.java"
        ));

        assertThat(lmsController)
                .doesNotContain("/subjects")
                .doesNotContain("/levels")
                .doesNotContain("/topics")
                .doesNotContain("adminPlaceholder")
                .doesNotContain("deprecatedBootstrapReplacement");
    }

    private String allMainJava() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            return files
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .map(this::readUnchecked)
                    .collect(Collectors.joining("\n"));
        }
    }

    private String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + path, ex);
        }
    }
}
