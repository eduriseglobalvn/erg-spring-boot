package com.erg.ergspringboot.audit.internal;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecorded;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.model.audit.AuditEvent;
import com.erg.ergspringboot.repository.audit.AuditEventRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional append boundary; callers never mutate the audit entity. */
@Service
public class AuditWriteService {
    private final AuditEventRepository repository;
    private final AuditRedactionPolicy redaction;
    private final AuditHashChain hashChain;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;
    private final int defaultRetentionDays;
    private final Clock clock;

    @Autowired
    public AuditWriteService(
            AuditEventRepository repository,
            AuditRedactionPolicy redaction,
            AuditHashChain hashChain,
            EntityManager entityManager,
            ApplicationEventPublisher events,
            @Value("${erg.audit.retention.default-days:365}") int defaultRetentionDays
    ) {
        if (defaultRetentionDays < 1 || defaultRetentionDays > 3650) {
            throw new IllegalArgumentException("audit retention must be between 1 and 3650 days");
        }
        this.repository = repository;
        this.redaction = redaction;
        this.hashChain = hashChain;
        this.entityManager = entityManager;
        this.events = events;
        this.defaultRetentionDays = defaultRetentionDays;
        this.clock = Clock.systemUTC();
    }

    @Transactional("postgresTransactionManager")
    public AuditEvent append(AuditRecord request) {
        lockTenant(request.tenantId());
        String idempotencyKey = normalize(request.idempotencyKey());
        if (idempotencyKey == null) idempotencyKey = "audit:" + IdGenerator.newId();
        if (idempotencyKey.length() > 192) throw new IllegalArgumentException("audit idempotency key is too long");

        AuditEvent existing = repository.findByTenantIdAndIdempotencyKey(request.tenantId(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!sameIntent(existing, request)) {
                throw new ConflictException("audit idempotency key conflicts with another event");
            }
            return existing;
        }

        OffsetDateTime occurredAt = request.occurredAt() == null
                ? OffsetDateTime.now(clock) : request.occurredAt().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime retentionUntil = request.retentionUntil() == null
                ? occurredAt.plusDays(defaultRetentionDays) : request.retentionUntil();
        AuditEvent event = new AuditEvent();
        event.setTenantId(request.tenantId());
        event.setActorId(normalize(request.actorId()));
        event.setScopeType(normalize(request.scopeType()));
        event.setScopeId(normalize(request.scopeId()));
        event.setAction(request.action());
        event.setResourceType(request.resourceType());
        event.setResourceId(normalize(request.resourceId()));
        event.setOutcome(request.outcome() == null ? AuditOutcome.SUCCESS : request.outcome());
        event.setRequestId(normalize(request.requestId()));
        event.setCorrelationId(normalize(request.correlationId(), event.getRequestId(), IdGenerator.newId()));
        event.setCausationId(normalize(request.causationId()));
        event.setDetails(redaction.redact(request.details()));
        event.setIdempotencyKey(idempotencyKey);
        event.setPreviousHash(repository.findTopByTenantIdOrderByOccurredAtDescIdDesc(request.tenantId())
                .map(AuditEvent::getEventHash).orElse(null));
        event.setOccurredAt(occurredAt);
        event.setRetentionUntil(retentionUntil);
        event.setRedactionVersion(AuditRedactionPolicy.VERSION);
        event.setLegalHold(false);
        event.setEventHash(hashChain.hash(event));
        AuditEvent saved = repository.saveAndFlush(event);
        if (events != null) {
            events.publishEvent(new AuditRecorded(
                    saved.getId(), 1, saved.getTenantId(), saved.getActorId(), saved.getAction(),
                    saved.getResourceType(), saved.getResourceId(), saved.getEventHash(),
                    saved.getCorrelationId(), saved.getCausationId(), saved.getOccurredAt()));
        }
        return saved;
    }

    private boolean sameIntent(AuditEvent existing, AuditRecord request) {
        return Objects.equals(existing.getAction(), request.action())
                && Objects.equals(existing.getResourceType(), request.resourceType())
                && Objects.equals(existing.getResourceId(), normalize(request.resourceId()))
                && Objects.equals(existing.getOutcome(), request.outcome() == null ? AuditOutcome.SUCCESS : request.outcome());
    }

    private void lockTenant(String tenantId) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(:tenantId))")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
    }

    private String normalize(String value, String fallback, String secondFallback) {
        String first = normalize(value);
        if (first != null) return first;
        first = normalize(fallback);
        return first == null ? secondFallback : first;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
