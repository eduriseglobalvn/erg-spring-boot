package com.erg.ergspringboot.reporting;

import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.reporting.ReportJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Public reporting facade. Web adapters do not authorize against JPA entities. */
@Service
public class ReportingOperations {
    private final ReportJobPort jobs;
    private final AssignmentScopeLookup assignments;
    private final ReportingAnalyticsPort analytics;

    @Autowired
    public ReportingOperations(ReportJobPort jobs, AssignmentScopeLookup assignments,
                               ReportingAnalyticsPort analytics) {
        this.jobs = jobs;
        this.statusReader = jobs::get;
        this.assignments = assignments;
        this.analytics = analytics;
    }

    public ReportingOperations(ReportJobPort jobs, AssignmentScopeLookup assignments) {
        this(jobs, assignments, null);
    }

    /** Compatibility constructor for the legacy report-job adapter. */
    public ReportingOperations(ReportJobPort jobs) {
        this(jobs, (tenantId, assignmentId) -> java.util.Optional.empty());
    }

    /** Compatibility constructor retained for legacy tests and adapters. */
    public ReportingOperations(ReportJobService jobs, AssignmentScopeLookup assignments) {
        this.jobs = new ReportJobPort() {
            @Override
            public ReportJobResponse enqueueAssignmentProgress(String tenantId, String requestedBy,
                    String assignmentId, String idempotencyKey) {
                return jobs.enqueueAssignmentProgress(tenantId, requestedBy, assignmentId, idempotencyKey);
            }

            @Override
            public ReportJobResponse get(String tenantId, String id) {
                return jobs.get(tenantId, id);
            }
        };
        this.statusReader = jobs::get;
        this.assignments = assignments;
        this.analytics = null;
    }

    private final JobStatusReader statusReader;

    public ReportJobResponse enqueueAssignmentProgress(
            UserAccessContext access, String assignmentId, String idempotencyKey
    ) {
        return enqueueAssignmentProgress(access, assignmentId, idempotencyKey, ReportExportOptions.defaults());
    }

    public ReportJobResponse enqueueAssignmentProgress(
            UserAccessContext access, String assignmentId, String idempotencyKey, ReportExportOptions options
    ) {
        AssignmentScopeLookup.AssignmentScope assignment = assignments.findByTenantIdAndId(access.tenantId(), assignmentId)
                .filter(item -> access.tenantId().equals(item.tenantId()))
                .orElseThrow(() -> new ForbiddenException("assignment is outside the current tenant"));
        if (!access.canAccessClass(assignment.academicClassId())) {
            throw new ForbiddenException("assignment is outside the current class scope");
        }
        ReportExportOptions safe = options == null ? ReportExportOptions.defaults() : options;
        if (safe.filter() != null && safe.filter().classId() != null
                && !safe.filter().classId().equals(assignment.academicClassId())) {
            throw new ForbiddenException("report class filter is outside the assignment scope");
        }
        return jobs.enqueueAssignmentProgress(access.tenantId(), access.userId(), assignmentId, idempotencyKey, safe);
    }

    public ReportJobResponse status(UserAccessContext access, String jobId) {
        return statusReader.get(access.tenantId(), jobId);
    }

    public ReportingResponses.AssignmentProgressReportResponse assignmentProgress(
            UserAccessContext access, String assignmentId, AssignmentProgressFilter filter) {
        if (analytics == null) throw new IllegalStateException("reporting analytics is not configured");
        return analytics.assignmentProgress(access, assignmentId, filter);
    }

    @FunctionalInterface
    private interface JobStatusReader {
        ReportJobResponse get(String tenantId, String id);
    }
}
