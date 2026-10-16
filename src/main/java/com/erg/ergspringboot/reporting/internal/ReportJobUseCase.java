package com.erg.ergspringboot.reporting.internal;

import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.reporting.ReportJob;
import com.erg.ergspringboot.repository.reporting.ReportJobRepository;
import com.erg.ergspringboot.reporting.ReportExportOptions;
import com.erg.ergspringboot.reporting.ReportJobPort;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns report-job persistence and idempotent enqueue semantics inside reporting. */
@Service
public class ReportJobUseCase implements ReportJobPort {
    public static final String ASSIGNMENT_PROGRESS = "ASSIGNMENT_PROGRESS";
    private final ReportJobRepository repository;

    public ReportJobUseCase(ReportJobRepository repository) {
        this.repository = repository;
    }

    @Transactional(value = "postgresTransactionManager")
    public ReportJobResponse enqueueAssignmentProgress(
            String tenantId, String requestedBy, String assignmentId, String idempotencyKey) {
        return enqueueAssignmentProgress(tenantId, requestedBy, assignmentId, idempotencyKey,
                ReportExportOptions.defaults());
    }

    @Override
    @Transactional(value = "postgresTransactionManager")
    public ReportJobResponse enqueueAssignmentProgress(
            String tenantId, String requestedBy, String assignmentId, String idempotencyKey,
            ReportExportOptions options) {
        ReportExportOptions safeOptions = options == null ? ReportExportOptions.defaults() : options;
        String format = safeOptions.format().toLowerCase(Locale.ROOT);
        if (!format.equals("json") && !format.equals("csv")) {
            throw new com.erg.ergspringboot.exception.BadRequestException("format must be json or csv");
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? ASSIGNMENT_PROGRESS + ":" + assignmentId + ":" + requestedBy + ":"
                        + OffsetDateTime.now().toEpochSecond()
                : idempotencyKey.trim();
        Map<String, Object> request = requestJson(assignmentId, format, safeOptions);
        ReportJob existing = repository.findByTenantIdAndIdempotencyKey(tenantId, key).orElse(null);
        if (existing != null) {
            if (!sameRequest(existing.getRequestJson(), request)) {
                throw new ConflictException("report idempotency key was reused with a different request");
            }
            return toResponse(existing);
        }
        OffsetDateTime now = OffsetDateTime.now();
        ReportJob job = new ReportJob();
        job.setId(IdGenerator.newId());
        job.setTenantId(tenantId);
        job.setRequestedBy(requestedBy);
        job.setReportType(ASSIGNMENT_PROGRESS);
        job.setAggregateId(assignmentId);
        job.setIdempotencyKey(key);
        job.setRequestJson(request);
        job.setStatus("queued");
        job.setProgress(0);
        job.setFormat(format);
        job.setAvailableAt(now);
        job.setCreatedAt(now);
        job.setExpiresAt(now.plus(Duration.ofDays(1)));
        return toResponse(repository.save(job));
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public ReportJobResponse get(String tenantId, String id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("report job not found"));
    }

    public ReportJobResponse toResponse(ReportJob job) {
        return new ReportJobResponse(
                job.getId(), job.getReportType(), job.getAggregateId(), job.getStatus(), job.getProgress(),
                job.getCreatedAt(), job.getCompletedAt(), job.getResultJson(), job.getLastError(),
                job.getFormat(), job.getExpiresAt(), "/api/v1/reports/jobs/" + job.getId() + "/download");
    }

    private Map<String, Object> requestJson(String assignmentId, String format, ReportExportOptions options) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("format", format);
        var filter = options.filter();
        put(request, "classId", filter.classId());
        put(request, "status", filter.status());
        put(request, "submittedFrom", filter.submittedFrom() == null ? null : filter.submittedFrom().toString());
        put(request, "submittedTo", filter.submittedTo() == null ? null : filter.submittedTo().toString());
        request.put("timezone", filter.timezone().getId());
        request.put("page", filter.page());
        request.put("size", filter.size());
        return request;
    }

    private boolean sameRequest(Map<String, Object> existing, Map<String, Object> requested) {
        if (existing == null) return false;
        Map<String, Object> normalized = new LinkedHashMap<>(existing);
        normalized.putIfAbsent("format", "json");
        normalized.putIfAbsent("timezone", "UTC");
        normalized.putIfAbsent("page", 0);
        normalized.putIfAbsent("size", 100);
        return requested.entrySet().stream().allMatch(entry ->
                java.util.Objects.equals(String.valueOf(normalized.get(entry.getKey())), String.valueOf(entry.getValue())));
    }

    private void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }
}
