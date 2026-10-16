package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarWorkspaceQueryArchitectureTest {

    @Test
    void workspaceOrchestrationHasAnInternalOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.calendar.internal.readmodel.CalendarWorkspaceQueryUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/calendar/internal/readmodel/CalendarReadUseCase.java"));
        assertThat(facade)
                .contains("workspaceQueries.lcms(")
                .contains("workspaceQueries.teacher(")
                .contains("workspaceQueries.student(");
    }
}
