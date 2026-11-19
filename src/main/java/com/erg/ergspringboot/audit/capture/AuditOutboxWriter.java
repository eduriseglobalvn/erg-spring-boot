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
import org.springframework.web.servlet.HandlerMapping;
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
                                     String resourceId, AuditOutcome outcome, String requestId,
                                     Map<String, Object> details) {
        String tenantId = SecurityUtils.normalizeTenant(principal.tenantId());
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("roles", principal.roles());
        merged.put("capturedAt", OffsetDateTime.now().toString());
        if (details != null) merged.putAll(details);
        return new AuditRecord(tenantId, principal.userId(), "TENANT", tenantId,
                cut(action, 128), cut(resourceType, 96), resourceId == null || resourceId.isBlank() ? null : cut(resourceId, 96), outcome, requestId, requestId, null, merged,
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
        // A 403 from the filter chain has no matched pattern, only the raw path with ids in it.
        String action = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) != null
                ? actionOf(request.getMethod(), pattern) : request.getMethod() + " " + pattern;
        return record(principal, action, resourceTypeOf(pattern), resourceIdOf(request), outcome,
                RequestCorrelation.current(request), details);
    }

    /**
     * Stable business name of a REST write (X-003), e.g. {@code notification-campaigns.send} for
     * {@code POST /api/v1/notification-campaigns/{id}/send} and {@code notification-campaigns.create} for
     * {@code POST /api/v1/notification-campaigns}. A literal right after a path variable is the verb;
     * otherwise the HTTP method gives it. The raw method and pattern stay in {@code details}.
     */
    static String actionOf(String method, String pattern) {
        List<String> names = new java.util.ArrayList<>();
        boolean afterVariable = false;
        boolean endsWithVerb = false;
        for (String segment : pattern.split("/")) {
            if (segment.isBlank() || (names.isEmpty() && (segment.equals("api") || segment.matches("v\\d+")))) continue;
            boolean variable = segment.startsWith("{");
            if (!variable) names.add(segment);
            endsWithVerb = !variable && afterVariable;
            afterVariable = variable;
        }
        if (names.isEmpty()) return method + " " + pattern;
        String name = String.join(".", names);
        if (endsWithVerb) return name;
        return name + "." + switch (method) {
            case "POST" -> "create";
            case "PUT", "PATCH" -> "update";
            case "DELETE" -> "delete";
            default -> method.toLowerCase(java.util.Locale.ROOT);
        };
    }

    /**
     * The target of the call: the last path variable, e.g. {@code {id}} of {@code /notification-campaigns/{id}/send};
     * for a create without one, the id of the returned object ({@link CreatedResourceIdAdvice}); else null.
     */
    static String resourceIdOf(HttpServletRequest request) {
        String last = null;
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables) {
            for (Object value : variables.values()) if (value != null) last = value.toString();
        }
        if (last == null && request.getAttribute(CreatedResourceIdAdvice.ATTRIBUTE) instanceof String created) return created;
        return last;
    }

    /** The {@code id} of a returned object: a Map entry, a record component or a getter; unwraps {@code ApiResponse.data}. */
    static String idOf(Object value) {
        if (value instanceof com.erg.ergspringboot.common.response.ApiResponse<?> envelope) value = envelope.data();
        if (value instanceof graphql.execution.DataFetcherResult<?> result) value = result.getData();
        if (value instanceof Map<?, ?> map) return map.get("id") instanceof String id && !id.isBlank() ? id : null;
        if (value == null || value instanceof CharSequence || value instanceof Iterable<?> || value.getClass().isArray()
                || value.getClass().getName().startsWith("java.")) return null;
        try {
            Object id = value.getClass().getMethod(value.getClass().isRecord() ? "id" : "getId").invoke(value);
            return id == null || id.toString().isBlank() ? null : id.toString();
        } catch (ReflectiveOperationException | RuntimeException noId) {
            return null;
        }
    }

    /** Target id of a GraphQL mutation: an {@code id} argument, or an {@code id} inside its {@code input} object. */
    static String argumentResourceId(Object arguments) {
        if (!(arguments instanceof Map<?, ?> map)) return null;
        if (map.get("id") instanceof String id) return id;
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?> nested && nested.get("id") instanceof String id) return id;
        }
        return null;
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
