package com.erg.ergspringboot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EventPublicationRegistryContractTest {

    @Test
    void flywayOwnsTheModulithJdbcRegistrySchema() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/postgres/V73__spring_modulith_event_publication.sql"));
        String configuration = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(migration)
                .contains("create table if not exists event_publication")
                .contains("listener_id text not null")
                .contains("completion_attempts int")
                .contains("last_resubmission_date timestamptz");
        assertThat(configuration).contains("schema-initialization:", "enabled: false");
        assertThat(configuration)
                .contains("registry-trigger-annotation: org.springframework.modulith.events.ApplicationModuleListener")
                .contains("completion-mode: delete");
    }

    @Test
    void calendarPublicationIsNotDuplicatedThroughTheLegacyOutbox() throws Exception {
        String worker = Files.readString(Path.of(
                "src/main/java/com/erg/ergspringboot/service/outbox/TransactionalOutboxWorker.java"));

        assertThat(worker).doesNotContain("SCHEDULE_PUBLISHED", "CalendarBatchPublished");
    }
}
