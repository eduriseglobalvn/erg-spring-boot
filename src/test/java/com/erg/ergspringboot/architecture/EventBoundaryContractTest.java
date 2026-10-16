package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EventBoundaryContractTest {
    @Test
    void eventPolicyDocumentsDurabilityAndAtLeastOnceSemantics() throws Exception {
        String policy = Files.readString(Path.of("docs/architecture/event-policy.md"));
        String catalog = Files.readString(Path.of("docs/architecture/event-catalog.md"));
        String event = Files.readString(Path.of("src/main/java/com/erg/ergspringboot/calendar/CalendarBatchPublished.java"));
        String listener = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/notifications/internal/CalendarBatchPublishedConsumer.java"));

        assertThat(policy).contains("at-least-once", "@ApplicationModuleListener", "JPA entities");
        assertThat(catalog).contains("CalendarBatchPublished", "schedule-published:<batchId>");
        assertThat(event).contains("eventId", "tenantId", "actorId", "schemaVersion");
        assertThat(listener).contains("@ApplicationModuleListener", "event.eventId()", "CalendarPublishedBatchProjection");
    }
}
