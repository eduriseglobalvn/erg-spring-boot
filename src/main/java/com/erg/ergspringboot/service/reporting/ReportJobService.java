package com.erg.ergspringboot.service.reporting;

import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;
import com.erg.ergspringboot.model.reporting.ReportJob;
import com.erg.ergspringboot.reporting.ReportingOperations;
import com.erg.ergspringboot.reporting.ReportingOperationsCompatibility;
import com.erg.ergspringboot.reporting.ReportJobPort;
import com.erg.ergspringboot.repository.reporting.ReportJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @deprecated Use {@link ReportingOperations}. This bean is a compatibility
 *             adapter for the worker and older integrations.
 */
@Deprecated
@Service
public class ReportJobService {
    public static final String ASSIGNMENT_PROGRESS = "ASSIGNMENT_PROGRESS";
    private final ReportJobPort operations;

    @Autowired
    public ReportJobService(ReportJobPort operations) {
        this.operations = operations;
    }

    /** Compatibility constructor for older tests and callers. */
    @Deprecated
    public ReportJobService(ReportJobRepository repository) {
        this(ReportingOperationsCompatibility.fromRepository(repository));
    }

    public ReportJobResponse enqueueAssignmentProgress(
            String tenantId, String requestedBy, String assignmentId, String idempotencyKey) {
        return operations.enqueueAssignmentProgress(tenantId, requestedBy, assignmentId, idempotencyKey);
    }

    public ReportJobResponse get(String tenantId, String id) {
        return operations.get(tenantId, id);
    }

    /** Legacy response helper retained for source compatibility. */
    public ReportJobResponse toResponse(ReportJob job) {
        return new ReportJobResponse(
                job.getId(), job.getReportType(), job.getAggregateId(), job.getStatus(), job.getProgress(),
                job.getCreatedAt(), job.getCompletedAt(), job.getResultJson(), job.getLastError(),
                job.getFormat(), job.getExpiresAt(), "/api/v1/reports/jobs/" + job.getId() + "/download");
    }

}
