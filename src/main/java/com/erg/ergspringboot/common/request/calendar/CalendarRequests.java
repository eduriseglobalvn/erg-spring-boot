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
            List<String> statuses,
            /** False drops the catalog lists from the response; null or true keeps them. */
            Boolean includeCatalog
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
            String note,
            Integer expectedVersion
    ) {
        public CalendarEventUpdateRequest(String tenantId, String eventId, String roomId, String roomName, String note) {
            this(tenantId, eventId, roomId, roomName, note, null);
        }
    }

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

    public enum CalendarResolveMode {
        REJECT,
        DELETE_CONFLICTING
    }
}
