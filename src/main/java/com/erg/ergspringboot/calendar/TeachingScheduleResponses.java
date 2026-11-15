package com.erg.ergspringboot.calendar;

import java.util.List;

/** GraphQL shapes of the teaching schedule v2 contract (KE_HOACH_LICH_DAY.md 4.2). Strings carry ISO dates/times. */
public final class TeachingScheduleResponses {

    private TeachingScheduleResponses() {
    }

    public record PeriodSlotResponse(int periodNo, String session, String startTime, String endTime) {}

    public record PeriodTemplateResponse(String id, String name, int periodsPerDay, List<PeriodSlotResponse> slots) {}

    /** Result of editTeachingScheduleSlot / swapTeachingScheduleTeacher; {@code applied} false for a dry run. */
    public record SlotChangeResponse(boolean applied, boolean blocking, int affectedCount, List<ChangedEventResponse> events,
            List<ConflictDetailResponse> conflicts) {}

    public record ChangedEventResponse(String eventId, String date, int periodNo, Integer version) {}

    public record SchoolPeriodAssignmentResponse(String schoolId, String schoolName, String templateId, String templateName,
            String effectiveFrom, boolean current) {}

    public record SchoolRowResponse(String schoolId, String schoolName, String schoolShortName,
            int conflictCount, boolean hasDraft, int periodCount) {}

    public record SchoolTravelTimeResponse(SchoolRowResponse schoolA, SchoolRowResponse schoolB, int minutes,
            String updatedAt) {}

    public record SideResponse(String eventId, String teacherId, String teacherName, String classId, String className,
            String roomId, String roomName, String schoolId, String schoolName, String schoolShortName,
            String subjectName, String batchId, String batchLabel, String batchStatus, String createdByName,
            String createdByRole, String createdAt, Integer periodNo, String startTime, String endTime) {}

    public record TravelGapResponse(String fromSchoolId, String fromSchoolShortName, String fromSchoolName,
            String toSchoolId, String toSchoolShortName, String toSchoolName, String previousEndTime, int gapMinutes,
            int requiredMinutes) {}

    public record FixSuggestionResponse(String kind, String targetEventId, String targetClientRowId,
            String targetClassName, String teacherId, String teacherName, String roomId, String roomName,
            String reason) {}

    public record LeaveInfoResponse(String fromDate, String toDate, String part) {}

    public record ConflictDetailResponse(String id, String kind, String severity, String clientRowId, int weekday,
            int periodNo, String startTime, String endTime, String firstDate, int affectedWeeks, int totalWeeks,
            SideResponse mine, SideResponse other, TravelGapResponse travel, LeaveInfoResponse leave,
            FixSuggestionResponse fix, String message) {}

    /** TeachingScheduleBatch of the contract; {@code version} is the optimistic-lock value clients echo back. */
    public record BatchResponse(String id, String schoolId, String label, String status, int version,
            String periodTemplateId, String applyFrom, Integer repeatWeeks, String publishedAt, int affectedEventCount) {}

    public record RowIdPairResponse(String clientRowId, String eventId) {}

    public record AccessClassChangeResponse(String classId, String className, String state, String revokeOn) {}

    public record AccessChangeResponse(String teacherId, String teacherName, int weeklyPeriods, String lastPeriodDate,
            String schoolId, String schoolState, String schoolRevokeOn, List<AccessClassChangeResponse> classes) {}

    public record DraftResultResponse(BatchResponse batch, int version, String savedAt, List<RowIdPairResponse> rowIdMap,
            List<ConflictDetailResponse> conflicts, List<AccessChangeResponse> accessChanges) {}

    // ---- slot options (B6) ----
    public record SlotClassOption(String classId, String className, Integer grade, String state, SideResponse busy) {}

    public record SlotTeacherOption(String teacherId, String teacherName, String initials, String state, int weeklyPeriods,
            SideResponse sameSchoolNeighbor, boolean taughtHereLastWeek, String classAccess, SideResponse busy,
            TravelGapResponse travel, LeaveInfoResponse leave) {}

    public record SlotRoomOption(String roomId, String roomName, Integer seatCount, String state, boolean usualForClass,
            SideResponse busy) {}

    public record SlotOptionsResponse(String schoolName, Integer grade, List<SlotClassOption> classes,
            List<SlotTeacherOption> teachers, List<SlotRoomOption> rooms) {}

    // ---- reuse a week (B7) ----
    public record HolidayResponse(String date, String name, String kind) {}

    public record TeacherCountResponse(String teacherId, String teacherName, int periods) {}

    public record ReuseWeekResponse(String weekStart, String weekEnd, int toCreate, int skipped, int conflicts,
            List<HolidayResponse> holidays, List<ConflictDetailResponse> conflictDetails) {}

    public record ReusePreviewResponse(int sourcePeriods, List<TeacherCountResponse> perTeacher,
            List<ConflictDetailResponse> excludedConflicts, List<ReuseWeekResponse> weeks, int totalToCreate) {}

    // ---- overview (B8) ----
    public record SchoolQuickCounts(int conflicts, int travel, int drafts, int empty) {}

    public record SchoolPageResponse(int total, List<SchoolRowResponse> items, SchoolQuickCounts quickCounts) {}

    public record OverviewCellResponse(String schoolId, int weekday, int periodNo, String eventId, String className,
            String roomName, String teacherName, String assistantName, String status, List<String> conflictKinds,
            String note, String batchLabel) {}

    public record OverviewGridResponse(String weekStart, PeriodTemplateResponse template, List<SchoolRowResponse> schools,
            List<OverviewCellResponse> cells, List<HolidayResponse> holidays, List<ConflictDetailResponse> conflicts) {}

    public record TeacherSlotResponse(int weekday, int periodNo, List<String> eventIds, List<String> classNames,
            List<String> schoolShortNames, boolean conflict, boolean travel, boolean onLeave, String status,
            List<String> roles) {}

    public record TeacherDayResponse(int weekday, List<String> schoolShortNames, boolean multiSchoolSameSession,
            TravelGapResponse travel, boolean onLeave) {}

    public record TeacherRowResponse(String teacherId, String teacherName, String initials, int schoolCount,
            int conflictCount, int weeklyPeriods, int maxPeriods, LeaveInfoResponse leave, AccessChangeResponse revokeNotice,
            List<TeacherDayResponse> days, List<TeacherSlotResponse> slots) {}

    public record TeacherQuickCounts(int conflicts, int travel, int multiSchool, int overload, int empty) {}

    public record TeacherPageResponse(int total, List<TeacherRowResponse> items, TeacherQuickCounts quickCounts) {}

    public record LoadSettingsResponse(int loadWarn, int loadMax, String updatedAt) {}
}
