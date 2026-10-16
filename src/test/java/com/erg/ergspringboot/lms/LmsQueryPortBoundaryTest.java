package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LmsQueryPortBoundaryTest {

    @Test
    void assignmentAndAttemptPortsAreTypedAndLoadable() {
        assertThatCode(() -> Class.forName("com.erg.ergspringboot.lms.LmsAssignmentQueryPort"))
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName("com.erg.ergspringboot.lms.LmsAttemptQueryPort"))
                .doesNotThrowAnyException();
    }

    @Test
    void publicReadPortsDoNotExposeEntitiesOrRepositories() throws Exception {
        for (String file : new String[]{
                "src/main/java/com/erg/ergspringboot/lms/LmsAssignmentQueryPort.java",
                "src/main/java/com/erg/ergspringboot/lms/LmsAttemptQueryPort.java"}) {
            String source = Files.readString(Path.of(file));
            assertThat(source)
                    .doesNotContain("model.lms.Assignment")
                    .doesNotContain("model.lms.Attempt")
                    .doesNotContain("extends JpaRepository")
                    .doesNotContain("@Entity");
        }
    }

    @Test
    void readUseCasesDelegatePersistenceToPorts() throws Exception {
        String progress = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/lms/internal/readmodel/LmsAssignmentProgressQueryUseCase.java"));
        String homework = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/lms/internal/readmodel/LmsTeacherHomeworkReadUseCase.java"));
        assertThat(progress).contains("LmsAttemptQueryPort").contains("LmsAssignmentQueryPort");
        assertThat(homework).contains("LmsAttemptQueryPort").contains("LmsAssignmentQueryPort");
    }
}
