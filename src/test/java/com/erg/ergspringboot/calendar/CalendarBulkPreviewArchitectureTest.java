package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarBulkPreviewArchitectureTest {

    @Test
    void previewHasAnInternalOrchestrationOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.calendar.internal.bulk.CalendarBulkPreviewUseCase"))
                .doesNotThrowAnyException();

        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/calendar/internal/command/CalendarCommandUseCase.java"));
        assertThat(facade)
                .contains("bulkPreview.preview(");
    }
}
