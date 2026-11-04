package com.erg.ergspringboot.reporting;

import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
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
        this.assignments = assignments;
        this.analytics = analytics;
    }

    public ReportingOperations(ReportJobPort jobs, AssignmentScopeLookup assignments) {
        this(jobs, assignments, null);
    }

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

    /**
     * B8: job status and download are readable by the requester, or by callers with class scope over the
     * reported assignment; a job id alone (logs, shared URLs) is not a capability.
     */
    public ReportJobResponse status(UserAccessContext access, String jobId) {
        ReportJobResponse job = jobs.get(access.tenantId(), jobId);
        boolean requester = access.userId() != null
                && jobs.requestedBy(access.tenantId(), jobId).filter(access.userId()::equals).isPresent();
        if (requester) return job;
        AssignmentScopeLookup.AssignmentScope assignment = job.aggregateId() == null ? null
                : assignments.findByTenantIdAndId(access.tenantId(), job.aggregateId())
                        .filter(item -> access.tenantId().equals(item.tenantId())).orElse(null);
        String classId = assignment == null ? null : assignment.academicClassId();
        boolean inScope = assignment != null && (classId == null || classId.isBlank()
                ? access.managedClassIds().contains("*")
                : access.canAccessClass(classId));
        if (!inScope) throw new ForbiddenException("report job is outside the current class scope");
        return job;
    }

    public ReportingResponses.AssignmentProgressReportResponse assignmentProgress(
            UserAccessContext access, String assignmentId, AssignmentProgressFilter filter) {
        if (analytics == null) throw new IllegalStateException("reporting analytics is not configured");
        return analytics.assignmentProgress(access, assignmentId, filter);
    }
}
