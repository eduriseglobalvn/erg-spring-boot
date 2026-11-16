package com.erg.ergspringboot.audit.capture;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.audit.internal.AuditRedactionPolicy;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import com.erg.ergspringboot.utils.IdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Appends a captured action to {@code audit_outbox} in its own short transaction (no audit_events tenant
 * lock on the business path). {@link AuditOutboxRelay} moves rows to {@code audit_events}. Secrets are
 * masked here, before anything is stored.
 *
 * <p>shortcut: written after the business transaction ended (interceptor/instrumentation run outside it),
 * so a crash between commit and this insert loses the record; upgrade by joining the service transaction.
 */
@Component
public class AuditOutboxWriter {
    private static final Logger log = LoggerFactory.getLogger(AuditOutboxWriter.class);
    static final String INSERT = "insert into audit_outbox (id, tenant_id, payload) values (?, ?, ?::jsonb)";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditRedactionPolicy redaction;
    private final TransactionTemplate transaction;

    public AuditOutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper, AuditRedactionPolicy redaction,
                             @Qualifier("postgresTransactionManager") PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.redaction = redaction;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Never throws: a failed audit write must not turn a finished business action into an error. */
    public void write(AuditRecord record) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(record.tenantId(), record.actorId(), "system"))) {
            String id = IdGenerator.newId();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", record.tenantId());
            payload.put("actorId", record.actorId());
            payload.put("scopeType", record.scopeType());
            payload.put("scopeId", record.scopeId());
            payload.put("action", record.action());
            payload.put("resourceType", record.resourceType());
            payload.put("resourceId", record.resourceId());
            payload.put("outcome", record.outcome().name());
            payload.put("requestId", record.requestId());
            payload.put("details", redaction.redact(record.details()));
            payload.put("idempotencyKey", "capture:" + id);
            String json = mapper.writeValueAsString(payload);
            transaction.executeWithoutResult(status -> jdbc.update(INSERT, id, record.tenantId(), json));
        } catch (RuntimeException failure) {
            log.error("Audit capture could not be stored action={} outcome={}", record.action(), record.outcome(), failure);
        }
    }

    /** Server-side record for an authenticated actor; {@code details} get the common fields added. */
    public static AuditRecord record(ErgPrincipal principal, String action, String resourceType,
                                     AuditOutcome outcome, String requestId, Map<String, Object> details) {
        String tenantId = SecurityUtils.normalizeTenant(principal.tenantId());
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("roles", principal.roles());
        merged.put("capturedAt", OffsetDateTime.now().toString());
        if (details != null) merged.putAll(details);
        return new AuditRecord(tenantId, principal.userId(), "TENANT", tenantId,
                cut(action, 128), cut(resourceType, 96), null, outcome, requestId, requestId, null, merged,
                null, OffsetDateTime.now(), null);
    }

    /** One audit record for a REST write, shared by the interceptor and the 403 filter-layer handler. */
    public static AuditRecord httpRecord(HttpServletRequest request, ErgPrincipal principal, String pattern,
                                         AuditOutcome outcome, int status, String handler) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", request.getMethod());
        details.put("path", request.getRequestURI());
        details.put("pattern", pattern);
        details.put("status", status);
        if (handler != null) details.put("handler", handler);
        details.put("ip", request.getRemoteAddr());
        details.put("userAgent", request.getHeader("User-Agent"));
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> params.put(key, values.length == 1 ? values[0] : List.of(values)));
        details.put("params", params);
        return record(principal, request.getMethod() + " " + pattern, resourceTypeOf(pattern), outcome,
                RequestCorrelation.current(request), details);
    }

    /** First meaningful path segment, e.g. {@code /api/v1/users/{id}/reset} gives {@code USERS}. */
    static String resourceTypeOf(String pattern) {
        for (String segment : pattern.split("/")) {
            if (segment.isBlank() || segment.equals("api") || segment.matches("v\\d+") || segment.startsWith("{")) continue;
            return segment.toUpperCase(java.util.Locale.ROOT);
        }
        return "HTTP";
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
