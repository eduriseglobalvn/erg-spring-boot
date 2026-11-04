package com.erg.ergspringboot.reporting;

import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;

/** Narrow report-job application port shared by the web facade and the report worker. */
public interface ReportJobPort {
    String ASSIGNMENT_PROGRESS = "ASSIGNMENT_PROGRESS";

    ReportJobResponse enqueueAssignmentProgress(
            String tenantId, String requestedBy, String assignmentId, String idempotencyKey);

    default ReportJobResponse enqueueAssignmentProgress(
            String tenantId, String requestedBy, String assignmentId, String idempotencyKey,
            ReportExportOptions options) {
        return enqueueAssignmentProgress(tenantId, requestedBy, assignmentId, idempotencyKey);
    }

    ReportJobResponse get(String tenantId, String id);

    /** The user that requested the job; empty when the adapter cannot tell (callers then require scope). */
    default java.util.Optional<String> requestedBy(String tenantId, String id) {
        return java.util.Optional.empty();
    }
}
