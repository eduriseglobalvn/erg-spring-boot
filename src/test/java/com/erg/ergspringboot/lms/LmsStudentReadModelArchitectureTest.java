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
        String legacy = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/service/lms/LmsRuntimeService.java"));
        assertThat(legacy).contains("studentReadModel.listAssignments(");
        assertThat(legacy).contains("studentReadModel.listScores(");
    }
}
