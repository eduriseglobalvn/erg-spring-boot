package com.erg.ergspringboot.controller.audit;

import com.erg.ergspringboot.audit.AuditEntry;
import com.erg.ergspringboot.audit.AuditOperations;
import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditPage;
import com.erg.ergspringboot.audit.AuditQuery;
import com.erg.ergspringboot.audit.authorization.AuditExport;
import com.erg.ergspringboot.audit.authorization.AuditRead;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/** Tenant/scope bounded audit read and export boundary. */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditOperations audit;
    private final ObjectMapper objectMapper;

    public AuditController(AuditOperations audit, ObjectMapper objectMapper) {
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/events")
    @AuditRead
    public ApiResponse<AuditPage> events(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            HttpServletRequest request
    ) {
        AuditQuery query = query(from, to, actorId, resourceType, resourceId, outcome, page, size);
        AuditPage data = audit.query(UserAccessContext.from(request), query);
        return ApiResponse.success(200, "Audit events loaded", data, request.getRequestURI(), requestId(request));
    }

    @GetMapping("/export")
    @AuditExport
    public ResponseEntity<?> export(
            @RequestParam(required = false, defaultValue = "json") String format,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size,
            HttpServletRequest request
    ) {
        String normalizedFormat = normalizeFormat(format);
        AuditQuery query = query(from, to, actorId, resourceType, resourceId, outcome, page, size);
        AuditPage data = audit.export(UserAccessContext.from(request), query, requestId(request));
        if ("json".equals(normalizedFormat)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.success(200, "Audit export generated", data,
                            request.getRequestURI(), requestId(request)));
        }
        byte[] csv = csv(data);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-events.csv")
                .body(csv);
    }

    private AuditQuery query(
            OffsetDateTime from,
            OffsetDateTime to,
            String actorId,
            String resourceType,
            String resourceId,
            String outcome,
            int page,
            int size
    ) {
        AuditOutcome parsedOutcome = null;
        if (outcome != null && !outcome.isBlank()) {
            try {
                parsedOutcome = AuditOutcome.valueOf(outcome.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("unsupported audit outcome");
            }
        }
        try {
            return new AuditQuery(from, to, actorId, resourceType, resourceId, parsedOutcome, page, size);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage());
        }
    }

    private byte[] csv(AuditPage page) {
        StringBuilder csv = new StringBuilder(512 + page.items().size() * 160);
        csv.append("id,tenantId,actorId,scopeType,scopeId,action,resourceType,resourceId,outcome,requestId,correlationId,causationId,eventHash,occurredAt,retentionUntil,details\n");
        for (AuditEntry entry : page.items()) {
            csv.append(cell(entry.id())).append(',')
                    .append(cell(entry.tenantId())).append(',')
                    .append(cell(entry.actorId())).append(',')
                    .append(cell(entry.scopeType())).append(',')
                    .append(cell(entry.scopeId())).append(',')
                    .append(cell(entry.action())).append(',')
                    .append(cell(entry.resourceType())).append(',')
                    .append(cell(entry.resourceId())).append(',')
                    .append(cell(entry.outcome() == null ? null : entry.outcome().name())).append(',')
                    .append(cell(entry.requestId())).append(',')
                    .append(cell(entry.correlationId())).append(',')
                    .append(cell(entry.causationId())).append(',')
                    .append(cell(entry.eventHash())).append(',')
                    .append(cell(entry.occurredAt())).append(',')
                    .append(cell(entry.retentionUntil())).append(',')
                    .append(cell(details(entry.details()))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String details(Object details) {
        try {
            return objectMapper.writeValueAsString(details == null ? java.util.Map.of() : details);
        } catch (RuntimeException exception) {
            throw new BadRequestException("audit details cannot be exported");
        }
    }

    private String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private String normalizeFormat(String format) {
        String normalized = format == null || format.isBlank() ? "json" : format.trim().toLowerCase(Locale.ROOT);
        if (!"json".equals(normalized) && !"csv".equals(normalized)) {
            throw new BadRequestException("audit export format must be json or csv");
        }
        return normalized;
    }

    private String requestId(HttpServletRequest request) {
        return RequestCorrelation.current(request);
    }
}
