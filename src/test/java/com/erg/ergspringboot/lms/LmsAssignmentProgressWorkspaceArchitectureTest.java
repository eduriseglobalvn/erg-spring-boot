package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LmsAssignmentProgressWorkspaceArchitectureTest {
    @Test
    void assignmentProgressWorkspaceIsOwnedByInternalQueryUseCase() throws Exception {
        assertThatCode(() -> Class.forName("com.erg.ergspringboot.lms.internal.readmodel.LmsAssignmentProgressQueryUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java"));
        assertThat(legacy).contains("assignmentProgressRead.workspace(");
    }
}
