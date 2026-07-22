package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarFullCatalogArchitectureTest {

    @Test
    void fullCatalogCompositionHasAnInternalOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.calendar.internal.readmodel.CalendarFullCatalogQueryUseCase"))
                .doesNotThrowAnyException();
        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/calendar/CalendarReadService.java"));
        assertThat(facade).contains("fullCatalogQueries.catalog(");
    }
}
