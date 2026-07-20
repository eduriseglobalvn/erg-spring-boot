package com.erg.ergspringboot.service.reporting;

import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.reporting.ReportJob;
import com.erg.ergspringboot.repository.reporting.ReportJobRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportJobService {
    public static final String ASSIGNMENT_PROGRESS = "ASSIGNMENT_PROGRESS";
    private final ReportJobRepository repository;

    public ReportJobService(ReportJobRepository repository) {
        this.repository = repository;
    }

    @Transactional(value = "postgresTransactionManager")
    public ReportJobResponse enqueueAssignmentProgress(
            String tenantId, String requestedBy, String assignmentId, String idempotencyKey
    ) {
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? ASSIGNMENT_PROGRESS + ":" + assignmentId + ":" + requestedBy + ":" + OffsetDateTime.now().toEpochSecond()
                : idempotencyKey.trim();
        ReportJob existing = repository.findByTenantIdAndIdempotencyKey(tenantId, key).orElse(null);
        if (existing != null) return toResponse(existing);
        ReportJob job = new ReportJob();
        job.setId(IdGenerator.newId());
        job.setTenantId(tenantId);
        job.setRequestedBy(requestedBy);
        job.setReportType(ASSIGNMENT_PROGRESS);
        job.setAggregateId(assignmentId);
        job.setIdempotencyKey(key);
        job.setRequestJson(Map.of("assignmentId", assignmentId));
        job.setStatus("queued");
        job.setProgress(0);
        job.setAvailableAt(OffsetDateTime.now());
        job.setCreatedAt(OffsetDateTime.now());
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
                job.getCreatedAt(), job.getCompletedAt(), job.getResultJson(), job.getLastError());
    }
}
