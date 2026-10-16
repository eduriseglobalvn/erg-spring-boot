package com.erg.ergspringboot.reporting;

import com.erg.ergspringboot.common.response.reporting.ReportJobResponses.ReportJobResponse;

/** Narrow report-job application port shared by web and compatibility adapters. */
public interface ReportJobPort {
    ReportJobResponse enqueueAssignmentProgress(
            String tenantId, String requestedBy, String assignmentId, String idempotencyKey);

    default ReportJobResponse enqueueAssignmentProgress(
            String tenantId, String requestedBy, String assignmentId, String idempotencyKey,
            ReportExportOptions options) {
        return enqueueAssignmentProgress(tenantId, requestedBy, assignmentId, idempotencyKey);
    }

    ReportJobResponse get(String tenantId, String id);
}
