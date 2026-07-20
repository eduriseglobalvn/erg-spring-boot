package com.erg.ergspringboot.reporting;

import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.reporting.ReportJobService;
import org.springframework.stereotype.Service;

/** Public reporting facade. Web adapters do not authorize against JPA entities. */
@Service
public class ReportingOperations {
    private final ReportJobService jobs;
    private final AssignmentScopeLookup assignments;

    public ReportingOperations(ReportJobService jobs, AssignmentScopeLookup assignments) {
        this.jobs = jobs;
        this.assignments = assignments;
    }

    public ReportJobResponse enqueueAssignmentProgress(
            UserAccessContext access, String assignmentId, String idempotencyKey
    ) {
        AssignmentScopeLookup.AssignmentScope assignment = assignments.findById(assignmentId)
                .filter(item -> access.tenantId().equals(item.tenantId()))
                .orElseThrow(() -> new ForbiddenException("assignment is outside the current tenant"));
        if (!access.canAccessClass(assignment.academicClassId())) {
            throw new ForbiddenException("assignment is outside the current class scope");
        }
        return jobs.enqueueAssignmentProgress(access.tenantId(), access.userId(), assignmentId, idempotencyKey);
    }

    public ReportJobResponse status(UserAccessContext access, String jobId) {
        return jobs.get(access.tenantId(), jobId);
    }
}
