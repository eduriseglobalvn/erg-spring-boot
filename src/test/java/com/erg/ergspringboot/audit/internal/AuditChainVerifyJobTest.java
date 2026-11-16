package com.erg.ergspringboot.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.persistence.AuditEvent;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AuditChainVerifyJobTest {
    private final AuditHashChain chain = new AuditHashChain(new ObjectMapper());

    @Test
    void chainVerifyDetectsTamper() {
        List<AuditEvent> events = new ArrayList<>();
        String previous = null;
        for (int i = 0; i < 3; i++) {
            AuditEvent e = event("e" + i, previous, i);
            previous = e.getEventHash();
            events.add(e);
        }
        assertThat(AuditChainVerifyJob.firstBreak(events, chain, true)).isNull();

        events.get(1).setAction("TAMPERED");
        assertThat(AuditChainVerifyJob.firstBreak(events, chain, true)).isEqualTo("e1");
    }

    @Test
    void chainVerifyDetectsBrokenLink() {
        AuditEvent a = event("a", null, 0);
        AuditEvent b = event("b", "not-a-hash", 1);
        assertThat(AuditChainVerifyJob.firstBreak(List.of(a, b), chain, true)).isEqualTo("b");
    }

    private AuditEvent event(String id, String previousHash, int n) {
        AuditEvent e = new AuditEvent();
        e.setId(id);
        e.setTenantId("tenant-a");
        e.setAction("ROLE_CHANGED");
        e.setResourceType("ROLE");
        e.setOutcome(AuditOutcome.SUCCESS);
        e.setDetails(Map.of("n", n));
        e.setIdempotencyKey("k" + n);
        e.setPreviousHash(previousHash);
        e.setOccurredAt(OffsetDateTime.parse("2026-08-09T00:00:00Z").plusSeconds(n));
        e.setRetentionUntil(OffsetDateTime.parse("2031-08-09T00:00:00Z"));
        e.setRedactionVersion(2);
        e.setEventHash(chain.hash(e));
        return e;
    }
}
