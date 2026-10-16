package com.erg.ergspringboot.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.audit.internal.AuditHashChain;
import com.erg.ergspringboot.audit.internal.AuditRedactionPolicy;
import com.erg.ergspringboot.audit.internal.AuditWriteService;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.model.audit.AuditEvent;
import com.erg.ergspringboot.repository.audit.AuditEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

class AuditWriteServiceTest {
    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query lockQuery = mock(Query.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuditWriteService service;

    AuditWriteServiceTest() {
        when(entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(:tenantId))"))
                .thenReturn(lockQuery);
        when(lockQuery.setParameter("tenantId", "tenant-a")).thenReturn(lockQuery);
        when(lockQuery.getSingleResult()).thenReturn(0);
        service = new AuditWriteService(repository, new AuditRedactionPolicy(),
                new AuditHashChain(new ObjectMapper()), entityManager, events, 365);
    }

    @Test
    void appendsRedactedHashChainedEventAndPublishesPostCommitSignal() {
        when(repository.findByTenantIdAndIdempotencyKey("tenant-a", "request-1"))
                .thenReturn(Optional.empty());
        when(repository.findTopByTenantIdOrderByOccurredAtDescIdDesc("tenant-a"))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            event.setId("audit-1");
            return event;
        }).when(repository).saveAndFlush(any(AuditEvent.class));

        AuditEvent saved = service.append(new AuditRecord(
                "tenant-a", "actor-a", "TENANT", "tenant-a", "USER_UPDATED", "USER", "user-a",
                AuditOutcome.SUCCESS, "request-1", null, null,
                Map.of("password", "secret", "email", "person@example.com"),
                "request-1", OffsetDateTime.parse("2026-08-09T00:00:00Z"), null));

        assertThat(saved.getId()).isEqualTo("audit-1");
        assertThat(saved.getDetails()).containsEntry("password", "[REDACTED]");
        assertThat(saved.getDetails().get("email")).asString().startsWith("sha256:");
        assertThat(saved.getEventHash()).hasSize(64).matches("[0-9a-f]{64}");
        verify(events).publishEvent(any(AuditRecorded.class));
    }

    @Test
    void replaysSameIdempotentIntentAndRejectsDifferentIntent() {
        AuditEvent existing = new AuditEvent();
        existing.setId("audit-1");
        existing.setTenantId("tenant-a");
        existing.setAction("USER_UPDATED");
        existing.setResourceType("USER");
        existing.setResourceId("user-a");
        existing.setOutcome(AuditOutcome.SUCCESS);
        when(repository.findByTenantIdAndIdempotencyKey("tenant-a", "request-1"))
                .thenReturn(Optional.of(existing));

        AuditRecord same = new AuditRecord(
                "tenant-a", "actor-a", null, null, "USER_UPDATED", "USER", "user-a",
                AuditOutcome.SUCCESS, null, null, null, Map.of(), "request-1", null, null);
        assertThat(service.append(same)).isSameAs(existing);

        AuditRecord different = new AuditRecord(
                "tenant-a", "actor-a", null, null, "USER_DELETED", "USER", "user-a",
                AuditOutcome.SUCCESS, null, null, null, Map.of(), "request-1", null, null);
        assertThatThrownBy(() -> service.append(different))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("idempotency");
    }
}
