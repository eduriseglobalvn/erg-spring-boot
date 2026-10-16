package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LmsAttemptUseCaseArchitectureTest {
    @Test
    void attemptCommandsAreOwnedByAnInternalUseCase() throws Exception {
        assertThatCode(() -> Class.forName("com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/lms/internal/runtime/LmsRuntimeUseCase.java"));
        assertThat(legacy).contains("attempts.start(");
        assertThat(legacy).doesNotContain("attemptService.findByStartIdempotencyKey(");
        assertThat(legacy).doesNotContain("attemptGradingService.gradeSubmission(");
    }
}
