package com.erg.ergspringboot.audit.internal;

import com.erg.ergspringboot.audit.AuditEntry;
import com.erg.ergspringboot.audit.AuditPage;
import com.erg.ergspringboot.audit.AuditQuery;
import com.erg.ergspringboot.model.audit.AuditEvent;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.audit.AuditEventRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant/scope-aware read model; persistence entities do not cross the facade. */
@Service
public class AuditQueryService {
    private final AuditEventRepository repository;
    private final AuditRedactionPolicy redaction;

    public AuditQueryService(AuditEventRepository repository, AuditRedactionPolicy redaction) {
        this.repository = repository;
        this.redaction = redaction;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public AuditPage query(UserAccessContext access, AuditQuery query) {
        if (access == null || access.tenantId() == null || access.tenantId().isBlank()) {
            throw new IllegalArgumentException("authenticated tenant is required");
        }
        AuditQuery safe = query == null ? AuditQuery.defaults() : query;
        List<String> educationUnits = safeIds(access.managedEducationUnitIds());
        List<String> classes = safeIds(access.managedClassIds());
        boolean allScopes = educationUnits.contains("*") || classes.contains("*");
        Page<AuditEvent> page = repository.search(
                access.tenantId(), safe.from(), safe.to(), safe.actorId(), safe.resourceType(), safe.resourceId(),
                safe.outcome(), allScopes, educationUnits, classes, PageRequest.of(safe.page(), safe.size()));
        return new AuditPage(
                page.map(this::entry).getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }

    private AuditEntry entry(AuditEvent event) {
        return new AuditEntry(
                event.getId(), event.getTenantId(), event.getActorId(), event.getScopeType(), event.getScopeId(),
                event.getAction(), event.getResourceType(), event.getResourceId(), event.getOutcome(),
                event.getRequestId(), event.getCorrelationId(), event.getCausationId(),
                redaction.redact(event.getDetails()), event.getEventHash(), event.getOccurredAt(),
                event.getRetentionUntil());
    }

    private List<String> safeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of("__NO_SCOPE__");
        return ids.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
    }
}
