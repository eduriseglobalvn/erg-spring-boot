package com.erg.ergspringboot.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ModuleEventMetadataTest {
    @Test
    void defaultsCorrelationToEventIdAndRejectsInvalidEnvelope() {
        ModuleEventMetadata metadata = new ModuleEventMetadata(
                "event-1", 1, "tenant-1", "actor-1", "aggregate-1", 3,
                OffsetDateTime.parse("2026-07-20T00:00:00Z"), null, null);
        assertThat(metadata.correlationId()).isEqualTo("event-1");
        assertThatThrownBy(() -> new ModuleEventMetadata(
                "", 1, "tenant-1", null, "aggregate-1", 0, OffsetDateTime.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
