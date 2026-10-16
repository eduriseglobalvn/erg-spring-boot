package com.erg.ergspringboot.reporting;

import com.erg.ergspringboot.platform.security.UserAccessContext;

/** Public reporting query port consumed by REST adapters. */
public interface ReportingAnalyticsPort {
    ReportingResponses.AssignmentProgressReportResponse assignmentProgress(
            UserAccessContext access, String assignmentId, AssignmentProgressFilter filter);
}
