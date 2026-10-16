package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LmsStudentReadModelArchitectureTest {
    @Test
    void studentReadsAreOwnedByReadModelUseCase() throws Exception {
        assertThatCode(() -> Class.forName("com.erg.ergspringboot.lms.internal.readmodel.LmsStudentReadModelUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/lms/internal/runtime/LmsRuntimeUseCase.java"));
        assertThat(legacy).contains("students.listAssignments(");
        assertThat(legacy).contains("students.listScores(");
        assertThat(legacy).contains("students.assignmentProgress(");
        assertThat(legacy).contains("students.quizStudents(");
        assertThat(legacy).doesNotContain("attemptService.findByAssignment(");
        assertThat(legacy).doesNotContain("attemptService.findByQuiz(");
    }
}
