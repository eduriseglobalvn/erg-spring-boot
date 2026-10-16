package com.erg.ergspringboot.reporting;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Validated, tenant-independent filter used by the assignment progress read model. */
public record AssignmentProgressFilter(
        String classId,
        String status,
        OffsetDateTime submittedFrom,
        OffsetDateTime submittedTo,
        ZoneId timezone,
        int page,
        int size
) {
    public AssignmentProgressFilter {
        page = Math.max(0, page);
        size = Math.min(100, Math.max(1, size));
        timezone = timezone == null ? ZoneId.of("UTC") : timezone;
    }
}
