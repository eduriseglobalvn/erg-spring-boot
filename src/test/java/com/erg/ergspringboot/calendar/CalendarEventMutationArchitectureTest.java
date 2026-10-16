package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarEventMutationArchitectureTest {

    @Test
    void eventMutationsHaveAnInternalCommandOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.calendar.internal.mutation.CalendarEventMutationUseCase"))
                .doesNotThrowAnyException();

        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/calendar/internal/command/CalendarCommandUseCase.java"));
        assertThat(facade)
                .contains("eventMutations.update(")
                .contains("eventMutations.delete(")
                .contains("eventMutations.deleteTeacherSchedule(")
                .doesNotContain("eventRepository.updateStatusBatch(tenantId, List.of(event.getId()")
                .doesNotContain("eventRepository.save(event);");
    }
}
