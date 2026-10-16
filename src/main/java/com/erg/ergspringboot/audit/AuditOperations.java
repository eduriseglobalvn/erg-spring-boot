package com.erg.ergspringboot.audit;

import com.erg.ergspringboot.audit.internal.AuditQueryService;
import com.erg.ergspringboot.audit.internal.AuditWriteService;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Public audit facade. Controllers and other modules depend on this boundary. */
@Service
public class AuditOperations {
    private final AuditWriteService writes;
    private final AuditQueryService reads;

    public AuditOperations(AuditWriteService writes, AuditQueryService reads) {
        this.writes = writes;
        this.reads = reads;
    }

    public AuditEntry record(AuditRecord record) {
        return toEntry(writes.append(record));
    }

    public AuditPage query(UserAccessContext access, AuditQuery query) {
        return reads.query(access, query);
    }

    /**
     * Export is intentionally synchronous and bounded. A future large export
     * can become a report job without changing the tenant/permission boundary.
     */
    public AuditPage export(UserAccessContext access, AuditQuery query, String requestId) {
        AuditQuery safe = query == null ? AuditQuery.defaults() : query;
        AuditPage page = reads.query(access, safe);
        String scopeType = access.currentClassId() == null ? "EDUCATION_UNIT" : "CLASS";
        String scopeId = access.currentClassId() == null
                ? access.currentEducationUnitId() : access.currentClassId();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("page", page.page());
        details.put("size", page.size());
        details.put("returned", page.items().size());
        details.put("format", "json-or-csv");
        record(new AuditRecord(
                access.tenantId(), access.userId(), scopeType, scopeId,
                "AUDIT_EXPORTED", "AUDIT_EVENT", null, AuditOutcome.SUCCESS,
                requestId, requestId, null, details,
                "audit-export:" + (requestId == null || requestId.isBlank()
                        ? OffsetDateTime.now().toInstant().toEpochMilli() : requestId)
                        + ":" + safe.page() + ":" + safe.size(),
                OffsetDateTime.now(), null));
        return page;
    }

    private AuditEntry toEntry(com.erg.ergspringboot.model.audit.AuditEvent event) {
        return new AuditEntry(
                event.getId(), event.getTenantId(), event.getActorId(), event.getScopeType(), event.getScopeId(),
                event.getAction(), event.getResourceType(), event.getResourceId(), event.getOutcome(),
                event.getRequestId(), event.getCorrelationId(), event.getCausationId(), event.getDetails(),
                event.getEventHash(), event.getOccurredAt(), event.getRetentionUntil());
    }
}
