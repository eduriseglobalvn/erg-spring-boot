package com.erg.ergspringboot.controller.reporting;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.reporting.AssignmentProgressFilter;
import com.erg.ergspringboot.reporting.ReportExportOptions;
import com.erg.ergspringboot.reporting.ReportingOperations;
import com.erg.ergspringboot.reporting.ReportingResponses.AssignmentProgressReportResponse;
import com.erg.ergspringboot.reporting.authorization.ReportingRead;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/jobs")
public class ReportJobController {
    private final ReportingOperations reporting;

    public ReportJobController(ReportingOperations reporting) {
        this.reporting = reporting;
    }

    @PostMapping("/assignment-progress")
    @ReportingRead
    public ApiResponse<ReportJobResponse> enqueue(
            @Valid @RequestBody AssignmentProgressJobRequest body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyHeader,
            HttpServletRequest request
    ) {
        UserAccessContext access = UserAccessContext.from(request);
        String idempotencyKey = idempotencyHeader == null || idempotencyHeader.isBlank()
                ? body.idempotencyKey() : idempotencyHeader;
        AssignmentProgressFilter filter = filter(body.classId(), body.status(), body.submittedFrom(), body.submittedTo(),
                body.timezone(), 0, 100);
        return ok("Report queued", reporting.enqueueAssignmentProgress(
                access, body.assignmentId(), idempotencyKey,
                new ReportExportOptions(body.format(), filter)), request);
    }

    @GetMapping("/{jobId}")
    @ReportingRead
    public ApiResponse<ReportJobResponse> status(@PathVariable String jobId, HttpServletRequest request) {
        UserAccessContext access = UserAccessContext.from(request);
        return ok("Report status loaded", reporting.status(access, jobId), request);
    }

    @GetMapping("/assignments/{assignmentId}/progress")
    @ReportingRead
    public ApiResponse<AssignmentProgressReportResponse> assignmentProgress(
            @PathVariable String assignmentId,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OffsetDateTime submittedFrom,
            @RequestParam(required = false) OffsetDateTime submittedTo,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request
    ) {
        AssignmentProgressFilter filter = filter(classId, status, submittedFrom, submittedTo, timezone,
                page == null ? 0 : page, size == null ? 100 : size);
        return ok("Assignment progress report loaded",
                reporting.assignmentProgress(UserAccessContext.from(request), assignmentId, filter), request);
    }

    @GetMapping("/{jobId}/download")
    @ReportingRead
    public ResponseEntity<?> download(@PathVariable String jobId, HttpServletRequest request) {
        ReportJobResponse job = reporting.status(UserAccessContext.from(request), jobId);
        if (!"completed".equalsIgnoreCase(job.status())) {
            throw new ConflictException("report is not ready for download");
        }
        if (job.expiresAt() != null && !job.expiresAt().isAfter(OffsetDateTime.now())) {
            throw new ResourceNotFoundException("report export has expired");
        }
        Map<String, Object> result = job.result() == null ? Map.of() : job.result();
        if ("csv".equalsIgnoreCase(job.format())) {
            String content = String.valueOf(result.getOrDefault("content", ""));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assignment-progress-" + job.aggregateId() + ".csv")
                    .body(content.getBytes(StandardCharsets.UTF_8));
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    public record AssignmentProgressJobRequest(
            @NotBlank String assignmentId,
            String idempotencyKey,
            String format,
            String classId,
            String status,
            OffsetDateTime submittedFrom,
            OffsetDateTime submittedTo,
            String timezone
    ) {
        public AssignmentProgressJobRequest(@NotBlank String assignmentId, String idempotencyKey) {
            this(assignmentId, idempotencyKey, "json", null, null, null, null, "UTC");
        }
    }

    private AssignmentProgressFilter filter(String classId, String status, OffsetDateTime submittedFrom,
                                             OffsetDateTime submittedTo, String timezone, int page, int size) {
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toLowerCase(Locale.ROOT);
        if (normalizedStatus != null && !java.util.Set.of("submitted", "in_progress", "needs_review", "review_required").contains(normalizedStatus)) {
            throw new BadRequestException("unsupported report status");
        }
        if (submittedFrom != null && submittedTo != null && submittedFrom.isAfter(submittedTo)) {
            throw new BadRequestException("submittedFrom must be before submittedTo");
        }
        ZoneId zone = timezone == null || timezone.isBlank() ? ZoneId.of("UTC") : parseZone(timezone);
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("page must be non-negative and size must be 1..100");
        return new AssignmentProgressFilter(blank(classId), normalizedStatus, submittedFrom, submittedTo, zone, page, size);
    }

    private ZoneId parseZone(String value) {
        try { return ZoneId.of(value.trim()); }
        catch (RuntimeException exception) { throw new BadRequestException("timezone is invalid"); }
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return com.erg.ergspringboot.platform.web.RequestCorrelation.current(request);
    }
}
