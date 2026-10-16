package com.erg.ergspringboot.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.audit.internal.AuditHashChain;
import com.erg.ergspringboot.model.audit.AuditEvent;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AuditHashChainTest {
    private final AuditHashChain hashChain = new AuditHashChain(new ObjectMapper());

    @Test
    void canonicalizesMapOrderAndBindsPreviousHashIntoTheChain() {
        AuditEvent first = event(new LinkedHashMap<>(Map.of("b", 2, "a", 1)));
        AuditEvent reordered = event(new LinkedHashMap<>(Map.of("a", 1, "b", 2)));
        AuditEvent linked = event(new LinkedHashMap<>(Map.of("a", 1, "b", 2)));
        linked.setPreviousHash("previous-event-hash");

        assertThat(hashChain.hash(first)).isEqualTo(hashChain.hash(reordered));
        assertThat(hashChain.hash(linked)).isNotEqualTo(hashChain.hash(reordered));
        assertThat(hashChain.hash(first)).hasSize(64).matches("[0-9a-f]{64}");
    }

    private AuditEvent event(Map<String, Object> details) {
        AuditEvent event = new AuditEvent();
        event.setTenantId("tenant-a");
        event.setActorId("actor-a");
        event.setScopeType("TENANT");
        event.setScopeId("tenant-a");
        event.setAction("ROLE_CHANGED");
        event.setResourceType("ROLE");
        event.setResourceId("role-a");
        event.setOutcome(AuditOutcome.SUCCESS);
        event.setRequestId("request-a");
        event.setCorrelationId("correlation-a");
        event.setIdempotencyKey("idempotency-a");
        event.setDetails(details);
        event.setOccurredAt(OffsetDateTime.parse("2026-08-09T00:00:00Z"));
        event.setRetentionUntil(OffsetDateTime.parse("2027-08-09T00:00:00Z"));
        event.setRedactionVersion(1);
        return event;
    }
}
