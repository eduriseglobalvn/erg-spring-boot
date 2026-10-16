package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarBatchLifecycleArchitectureTest {

    @Test
    void batchPublishAndCancelHaveAnInternalCommandOwner() throws Exception {
        assertThatCode(() -> Class.forName(
                "com.erg.ergspringboot.calendar.internal.batch.CalendarBatchLifecycleUseCase"))
                .doesNotThrowAnyException();

        String facade = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/calendar/internal/command/CalendarCommandUseCase.java"));
        assertThat(facade)
                .contains("batchLifecycle.publish(")
                .contains("batchLifecycle.cancel(")
                .doesNotContain("eventPublisher.publishEvent(new CalendarBatchPublished(");
    }
}
