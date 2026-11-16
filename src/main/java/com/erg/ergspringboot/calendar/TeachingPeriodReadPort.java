package com.erg.ergspringboot.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Every teacher's periods of a tenant for the teaching-hours sheets (cong tiet day): one row per event and teacher.
 * No principal: the caller has already decided who may read the sheets.
 */
public interface TeachingPeriodReadPort {

    /** Counted periods. */
    String PUBLISHED = "PUBLISHED";
    /** Periods taken off the timetable (not counted, no class log needed). */
    String CANCELLED = "CANCELLED";

    /** Periods starting on the Asia/Ho_Chi_Minh days {@code from}..{@code to} inclusive, with the given event status. */
    List<TeachingPeriod> periods(String tenantId, LocalDate from, LocalDate to, String status);

    /** {@code role} is {@code MAIN} or {@code ASSISTANT}; names are empty strings when the row is gone. */
    record TeachingPeriod(
            String eventId, String teacherUserId, String teacherName, String role, String classId, String className,
            String subjectId, String subjectName, String schoolId, String schoolName, String levelId, String levelName,
            Instant start, Instant end, int periodStart, int periodCount) {}
}
