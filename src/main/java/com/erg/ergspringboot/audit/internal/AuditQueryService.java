package com.erg.ergspringboot.audit.internal;

import com.erg.ergspringboot.audit.AuditEntry;
import com.erg.ergspringboot.audit.AuditPage;
import com.erg.ergspringboot.audit.AuditQuery;
import com.erg.ergspringboot.audit.persistence.AuditEvent;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.audit.persistence.AuditEventRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant/scope-aware read model; persistence entities do not cross the facade. */
@Service
public class AuditQueryService {
    private final AuditEventRepository repository;
    private final AuditRedactionPolicy redaction;

    private static final OffsetDateTime MIN_TIME = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime MAX_TIME = OffsetDateTime.of(9999, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC);

    /** Total per full filter (tenant and scope sets included): up to 30 s stale, bounded; never shared across filters. */
    private final Cache<Object, Long> totals = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(2_000).build();

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
        OffsetDateTime from = safe.from() == null ? MIN_TIME : safe.from();
        OffsetDateTime to = safe.to() == null ? MAX_TIME : safe.to();
        Slice<AuditEvent> slice = repository.search(
                access.tenantId(), from, to, safe.actorId(), safe.resourceType(), safe.resourceId(),
                safe.outcome(), allScopes, educationUnits, classes, PageRequest.of(safe.page(), safe.size()));
        List<AuditEntry> items = slice.getContent().stream().map(this::entry).toList();
        long seen = (long) slice.getNumber() * slice.getSize() + items.size();
        // The last page knows the exact total; otherwise the tenant-wide count is shared for a few seconds.
        long total = slice.hasNext()
                ? Math.max(seen + 1, totals.get(
                        List.of(access.tenantId(), from, to, String.valueOf(safe.actorId()), String.valueOf(safe.resourceType()),
                                String.valueOf(safe.resourceId()), String.valueOf(safe.outcome()), allScopes, educationUnits, classes),
                        key -> repository.countSearch(access.tenantId(), from, to, safe.actorId(), safe.resourceType(),
                                safe.resourceId(), safe.outcome(), allScopes, educationUnits, classes)))
                : seen;
        int size = Math.max(1, slice.getSize());
        return new AuditPage(items, slice.getNumber(), slice.getSize(), total, (int) Math.ceil((double) total / size),
                slice.hasNext());
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
