package com.erg.ergspringboot.common.response.calendar;

import java.util.List;

public final class CalendarResponses {

    private CalendarResponses() {}

    private static <T> List<T> listOrEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nonBlankOr(String value, String fallback) {
        if (value != null && !value.isBlank()) return value;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return "Chưa xác định";
    }

    public record CalendarWorkspaceResponse(
            String tenantId,
            String from,
            String to,
            String viewMode,
            List<CalendarEventResponse> events,
            CalendarFilterOptionsResponse filterOptions,
            List<CalendarLaneSummaryResponse> laneSummaries,
            CalendarCatalogResponse catalog,
            CalendarPermissionsResponse permissions,
            String generatedAt
    ) {
        public CalendarWorkspaceResponse {
            tenantId = nonBlankOr(tenantId, "default");
            from = nonBlankOr(from, "1970-01-01");
            to = nonBlankOr(to, from);
            viewMode = nonBlankOr(viewMode, "ALL");
            events = listOrEmpty(events);
            filterOptions = filterOptions == null
                    ? new CalendarFilterOptionsResponse(List.of(), List.of(), List.of(), List.of(), List.of())
                    : filterOptions;
            laneSummaries = listOrEmpty(laneSummaries);
            catalog = catalog == null
                    ? new CalendarCatalogResponse(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
                    : catalog;
            permissions = permissions == null
                    ? new CalendarPermissionsResponse(false, false, false, false, false)
                    : permissions;
            generatedAt = nonBlankOr(generatedAt, "1970-01-01T00:00:00Z");
        }
    }

    public record CalendarEventResponse(
            String id,
            String batchId,
            String title,
            String startAt,
            String endAt,
            String date,
            String startTime,
            String endTime,
            String schoolId,
            String schoolName,
            String classId,
            String className,
            String subjectId,
            String subjectName,
            String levelId,
            String levelName,
            String roomId,
            String roomName,
            int periodStart,
            int periodCount,
            List<CalendarTeacherAssignmentResponse> teachers,
            String status,
            String note,
            CalendarColorGroupResponse colorGroup
    ) {
        public CalendarEventResponse {
            id = nonBlankOr(id, batchId);
            title = nonBlankOr(title, id);
            startAt = blankToNull(startAt);
            endAt = blankToNull(endAt);
            date = blankToNull(date);
            startTime = blankToNull(startTime);
            endTime = blankToNull(endTime);
            schoolId = nonBlankOr(schoolId, "unknown-school");
            schoolName = nonBlankOr(schoolName, schoolId);
            classId = nonBlankOr(classId, "unknown-class");
            className = nonBlankOr(className, classId);
            teachers = listOrEmpty(teachers);
            status = nonBlankOr(status, "DRAFT");
            colorGroup = colorGroup == null
                    ? new CalendarColorGroupResponse("default", "default", "subject")
                    : colorGroup;
        }
    }

    public record CalendarTeacherAssignmentResponse(
            String userId,
            String displayName,
            String role,
            String colorKey
    ) {
        public CalendarTeacherAssignmentResponse {
            displayName = nonBlankOr(displayName, userId);
            role = nonBlankOr(role, "MAIN");
        }
    }

    public record CalendarFilterOptionsResponse(
            List<CalendarFilterOptionResponse> schools,
            List<CalendarFilterOptionResponse> classes,
            List<CalendarFilterOptionResponse> teachers,
            List<CalendarFilterOptionResponse> subjects,
            List<CalendarFilterOptionResponse> levels
    ) {
        public CalendarFilterOptionsResponse {
            schools = listOrEmpty(schools);
            classes = listOrEmpty(classes);
            teachers = listOrEmpty(teachers);
            subjects = listOrEmpty(subjects);
            levels = listOrEmpty(levels);
        }
    }

    public record CalendarFilterOptionResponse(
            String id,
            String label,
            int count,
            String colorKey
    ) {
        public CalendarFilterOptionResponse {
            label = nonBlankOr(label, id);
        }
    }

    public record CalendarLaneSummaryResponse(
            String id,
            String label,
            int count,
            String colorKey
    ) {
        public CalendarLaneSummaryResponse {
            label = nonBlankOr(label, id);
        }
    }

    public record CalendarColorGroupResponse(
            String key,
            String label,
            String kind
    ) {
        public CalendarColorGroupResponse {
            key = nonBlankOr(key, "default");
            kind = nonBlankOr(kind, "subject");
            label = nonBlankOr(label, key);
        }
    }

    public record CalendarCatalogResponse(
            List<CalendarFilterOptionResponse> schools,
            List<CalendarClassOptionResponse> classes,
            List<CalendarTeacherOptionResponse> teachers,
            List<CalendarTeacherOptionResponse> assistantTeachers,
            List<CalendarSubjectOptionResponse> subjects,
            List<CalendarLevelOptionResponse> levels,
            List<CalendarRoomOptionResponse> rooms
    ) {
        public CalendarCatalogResponse {
            schools = listOrEmpty(schools);
            classes = listOrEmpty(classes);
            teachers = listOrEmpty(teachers);
            assistantTeachers = listOrEmpty(assistantTeachers);
            subjects = listOrEmpty(subjects);
            levels = listOrEmpty(levels);
            rooms = listOrEmpty(rooms);
        }
    }

    public record CalendarClassOptionResponse(
            String id,
            String schoolId,
            String label,
            String grade,
            String academicYear
    ) {
        public CalendarClassOptionResponse {
            id = nonBlankOr(id, schoolId);
            schoolId = nonBlankOr(schoolId, "unknown-school");
            label = nonBlankOr(label, id);
        }
    }

    public record CalendarTeacherOptionResponse(
            String id,
            String label,
            String colorKey
    ) {
        public CalendarTeacherOptionResponse {
            id = nonBlankOr(id, label);
            label = nonBlankOr(label, id);
        }
    }

    public record CalendarSubjectOptionResponse(
            String id,
            String label
    ) {
        public CalendarSubjectOptionResponse {
            id = nonBlankOr(id, label);
            label = nonBlankOr(label, id);
        }
    }

    public record CalendarLevelOptionResponse(
            String id,
            String subjectId,
            String label,
            Integer sortOrder
    ) {
        public CalendarLevelOptionResponse {
            id = nonBlankOr(id, subjectId);
            subjectId = nonBlankOr(subjectId, "unknown-subject");
            label = nonBlankOr(label, id);
        }
    }

    public record CalendarRoomOptionResponse(
            String id,
            String label,
            String schoolId
    ) {
        public CalendarRoomOptionResponse {
            label = nonBlankOr(label, id);
        }
    }

    public record CalendarRoomOption(
            String id,
            String label,
            String schoolId
    ) {
        public CalendarRoomOption {
            label = nonBlankOr(label, id);
        }
    }

    public record CalendarPermissionsResponse(
            boolean canCreate,
            boolean canUpdate,
            boolean canDelete,
            boolean canPublish,
            boolean canViewAggregate
    ) {}

    public record CalendarPreviewPayload(
            boolean valid,
            int previewEventCount,
            int teacherScheduleCount,
            int totalPeriodCount,
            List<CalendarRowResultResponse> rowResults,
            List<CalendarConflictResponse> conflicts
    ) {}

    public record CalendarBulkPayload(
            String batchId,
            int createdEventCount,
            int teacherScheduleCount,
            int totalPeriodCount,
            List<CalendarRowResultResponse> rowResults,
            List<CalendarConflictResponse> conflicts
    ) {}

    public record CalendarEventPayload(
            CalendarEventResponse event
    ) {}

    public record CalendarDeletePayload(
            String eventId,
            boolean deleted,
            int affectedEventCount
    ) {}

    public record CalendarBatchPayload(
            String batchId,
            String status,
            int affectedEventCount
    ) {}

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

    public record CalendarRowResultResponse(
            String clientRowId,
            boolean valid,
            int eventCount,
            int teacherScheduleCount,
            List<CalendarRowMessageResponse> errors,
            List<CalendarRowMessageResponse> warnings
    ) {}

    public record CalendarRowMessageResponse(
            String code,
            String field,
            String message
    ) {}

    public record CalendarConflictResponse(
            String code,
            String severity,
            String clientRowId,
            String existingEventId,
            String message
    ) {}
}
