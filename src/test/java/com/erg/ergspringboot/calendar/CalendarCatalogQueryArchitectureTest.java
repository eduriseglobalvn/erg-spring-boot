package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarCatalogQueryArchitectureTest {
    @Test
    void activeRoomCatalogHasAnInternalOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.calendar.internal.readmodel.CalendarCatalogQueryUseCase"))
                .doesNotThrowAnyException();
        String legacy = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/calendar/CalendarReadService.java"));
        assertThat(legacy).contains("catalogQueries.activeRooms(");
    }
}
