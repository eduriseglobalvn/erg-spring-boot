package com.erg.ergspringboot.calendar;

import java.util.List;

/** Schedule batch read models of the calendar module (split from CalendarResponses). */
public final class CalendarBatchResponses {

    private CalendarBatchResponses() {}

    public record CalendarBatchPageResponse(
            List<CalendarBatchSummaryResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {}

    public record CalendarBatchSummaryResponse(
            String id,
            String tenantId,
            String schoolId,
            String academicYear,
            String applyFrom,
            Integer repeatWeeks,
            String status,
            String source,
            String note,
            String createdBy,
            String publishedBy,
            String publishedAt,
            String createdAt
    ) {}

    public record CalendarBatchDetailResponse(
            String id,
            String tenantId,
            String schoolId,
            String academicYear,
            String applyFrom,
            Integer repeatWeeks,
            String status,
            String source,
            String note,
            String createdBy,
            String publishedBy,
            String publishedAt,
            String createdAt,
            int eventCount
    ) {}
}
