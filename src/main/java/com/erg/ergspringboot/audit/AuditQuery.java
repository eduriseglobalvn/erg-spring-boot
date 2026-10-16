package com.erg.ergspringboot.audit;

import java.time.OffsetDateTime;

/** Bounded audit query. Pagination is deliberately capped at the boundary. */
public record AuditQuery(
        OffsetDateTime from,
        OffsetDateTime to,
        String actorId,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        int page,
        int size
) {
    public AuditQuery {
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (size < 1 || size > 1000) throw new IllegalArgumentException("size must be between 1 and 1000");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        actorId = blank(actorId);
        resourceType = blank(resourceType);
        resourceId = blank(resourceId);
    }

    public static AuditQuery defaults() {
        return new AuditQuery(null, null, null, null, null, null, 0, 100);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
