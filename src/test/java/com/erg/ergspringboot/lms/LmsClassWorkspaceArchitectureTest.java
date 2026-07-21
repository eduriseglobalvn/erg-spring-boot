package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LmsClassWorkspaceArchitectureTest {
    @Test
    void classWorkspaceIsOwnedByInternalReadUseCase() throws Exception {
        assertThatCode(() -> Class.forName("com.erg.ergspringboot.lms.internal.readmodel.LmsClassWorkspaceReadUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/service/lms/readmodel/LmsReadModelService.java"));
        assertThat(legacy).contains("classWorkspaceRead.query(");
        assertThat(legacy).contains("classWorkspaceRead.schoolClasses(");
    }
}
