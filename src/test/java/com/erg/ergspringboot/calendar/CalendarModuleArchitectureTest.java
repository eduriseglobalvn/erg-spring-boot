package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarModuleArchitectureTest {

    @Test
    void restAdapterUsesCalendarFacadeInsteadOfLegacyRepositories() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/controller/calendar/rest/CalendarRestController.java"));

        assertThat(controller)
                .contains("CalendarOperations")
                .doesNotContain("repository.calendar", "CalendarCommandService", "CalendarReadService", "CalendarRoomRepository");
    }

    @Test
    void facadeDoesNotExposePersistenceTypes() throws Exception {
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/calendar/CalendarOperations.java"));

        assertThat(facade)
                .doesNotContain("Repository", "model.calendar", "model.lms");
    }
}
