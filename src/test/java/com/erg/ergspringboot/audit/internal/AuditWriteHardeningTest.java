package com.erg.ergspringboot.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.audit.persistence.AuditEvent;
import com.erg.ergspringboot.audit.persistence.AuditEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

class AuditWriteHardeningTest {
    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditWriteService service;

    AuditWriteHardeningTest() {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(any(String.class))).thenReturn(q);
        when(q.setParameter(any(String.class), any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(0);
        when(repository.findByTenantIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(repository.findTopByTenantIdOrderByOccurredAtDescIdDesc(any())).thenReturn(Optional.empty());
        // JPA assigns the id on persist; the mock must too, because AuditRecorded requires an eventId.
        doAnswer(i -> {
            AuditEvent saved = i.getArgument(0);
            saved.setId("evt-" + Math.random());
            return saved;
        }).when(repository).saveAndFlush(any(AuditEvent.class));
        service = new AuditWriteService(repository, new AuditRedactionPolicy(),
                new AuditHashChain(new ObjectMapper()), em, mock(ApplicationEventPublisher.class), 1825);
    }

    private AuditEvent append(OffsetDateTime occurredAt, OffsetDateTime retentionUntil) {
        return service.append(new AuditRecord("t", "a", null, null, "X", "Y", "1", AuditOutcome.SUCCESS,
                null, null, null, Map.of(), "k-" + Math.random(), occurredAt, retentionUntil));
    }

    @Test
    void occurredAtIgnoresCaller() {
        AuditEvent e = append(OffsetDateTime.parse("2020-01-01T00:00:00Z"), null);
        assertThat(Duration.between(e.getOccurredAt(), OffsetDateTime.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void retentionNotShorterThanPolicy() {
        AuditEvent shorter = append(null, OffsetDateTime.now().plusDays(1));
        assertThat(shorter.getRetentionUntil()).isAfter(OffsetDateTime.now().plusDays(1824));
        AuditEvent longer = append(null, OffsetDateTime.now().plusDays(3000));
        assertThat(longer.getRetentionUntil()).isAfter(OffsetDateTime.now().plusDays(2999));
    }

    @Test
    void missingHmacSecretFailsOutsideDev() {
        for (String profile : new String[] {"prod", "staging", "local"}) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles(profile);
            assertThatThrownBy(() -> new AuditRedactionPolicy("", env)).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> new AuditRedactionPolicy("", new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
        for (String profile : new String[] {"dev", "test"}) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles(profile);
            assertThat(new AuditRedactionPolicy("", env)).isNotNull();
        }
    }
}
