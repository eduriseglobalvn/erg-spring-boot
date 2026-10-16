package com.erg.ergspringboot.common.response.reporting;

import java.time.OffsetDateTime;
import java.util.Map;

public final class ReportJobResponses {
    private ReportJobResponses() { }

    public record ReportJobResponse(
            String id,
            String reportType,
            String aggregateId,
            String status,
            int progress,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt,
            Map<String, Object> result,
            String error,
            String format,
            OffsetDateTime expiresAt,
            String downloadPath
    ) {
        public ReportJobResponse(String id, String reportType, String aggregateId, String status,
                                 int progress, OffsetDateTime createdAt, OffsetDateTime completedAt,
                                 Map<String, Object> result, String error) {
            this(id, reportType, aggregateId, status, progress, createdAt, completedAt, result, error,
                    "json", null, null);
        }
    }
}
