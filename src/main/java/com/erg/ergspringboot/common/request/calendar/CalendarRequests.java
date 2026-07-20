package com.erg.ergspringboot.common.request.calendar;

import java.util.Collections;
import java.util.List;

public final class CalendarRequests {

    private CalendarRequests() {}

    public record CalendarWorkspaceRequest(
            String tenantId,
            String from,
            String to,
            String viewMode,
            List<String> schoolIds,
            List<String> teacherIds,
            List<String> classIds,
            List<String> subjectIds,
            List<String> levelIds,
            List<String> statuses
    ) {
        public static CalendarWorkspaceRequest empty() {
            return new CalendarWorkspaceRequest(
                    null, null, null, null,
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList()
            );
        }
    }

    public record MyCalendarRequest(
            String tenantId,
            String from,
            String to,
            String viewMode,
            List<String> schoolIds,
            List<String> classIds,
            List<String> subjectIds,
            List<String> levelIds,
            List<String> statuses
    ) {}

    public record MyStudentCalendarInput(
            String tenantId,
            String from,
            String to,
            String viewMode,
            List<String> subjectIds,
            List<String> levelIds,
            List<String> statuses
    ) {}

    public record CalendarBulkCreateRequest(
            String tenantId,
            String idempotencyKey,
            String schoolId,
            String academicYear,
            String applyFrom,
            int repeatWeeks,
            String note,
            boolean overrideConflicts,
            String resolveMode,
            List<CalendarBulkRowRequest> rows
    ) {}

    public record CalendarBulkRowRequest(
            String clientRowId,
            List<String> classIds,
            int weekday,
            String session,
            int periodStart,
            int periodCount,
            String startTime,
            String endTime,
            String subjectId,
            String levelId,
            String roomId,
            String roomName,
            List<String> mainTeacherIds,
            List<String> assistantTeacherIds,
            String note
    ) {}

    public record CalendarEventUpdateRequest(
            String tenantId,
            String eventId,
            String roomId,
            String roomName,
            String note
    ) {}

    public record CalendarEventDeleteRequest(
            String tenantId,
            String eventId
    ) {}

    public record TeachingScheduleBatchListRequest(
            String tenantId,
            String schoolId,
            String status,
            Integer page,
            Integer size
    ) {}

    public record TeachingScheduleBatchDetailRequest(
            String tenantId,
            String batchId
    ) {}

    public record DeleteTeacherScheduleRequest(
            String tenantId,
            String teacherUserId,
            List<String> schoolIds,
            String dateFrom,
            String dateTo
    ) {}

    public record PublishBatchRequest(
            String batchId
    ) {}

    public record CancelBatchRequest(
            String batchId
    ) {}

    public record CreateRoomRequest(
            String tenantId,
            String schoolId,
            String roomName
    ) {}

    /**
     * Request DTO for Excel-based teaching schedule import.
     * This is a stub/framework record for future Excel import support.
     * The actual file data (byte[]) and fileName are used by the REST endpoint
     * which receives the Excel file via MultipartFile. The GraphQL mutation
     * only accepts metadata fields; the file upload flows through the REST endpoint.
     *
     * Future implementation will:
     * 1. Validate file extension (.xlsx, .xls)
     * 2. Parse Excel workbook into rows
     * 3. Validate column headers (classIds, weekday, period, time, subject, teachers)
     * 4. Convert rows to CalendarBulkRowRequest entries
     * 5. Delegate to the standard bulk create/preview pipeline
     */
    public record CalendarExcelImportRequest(
            String tenantId,
            String schoolId,
            String academicYear,
            String applyFrom,
            int repeatWeeks,
            String note,
            boolean overrideConflicts,
            String resolveMode,
            byte[] fileData,
            String fileName
    ) {}

    public enum CalendarResolveMode {
        REJECT,
        DELETE_CONFLICTING
    }
}
