package com.erg.ergspringboot.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    default List<TeachingPeriod> periods(String tenantId, LocalDate from, LocalDate to, String status) {
        return periods(tenantId, from, to, status, null, null);
    }

    /** As above, narrowed in SQL to one school and/or one teacher; null means no narrowing. */
    List<TeachingPeriod> periods(String tenantId, LocalDate from, LocalDate to, String status, String schoolId,
            String teacherUserId);

    /** Teachers holding at least one PUBLISHED period in the range: what "everyone confirmed" is measured against. */
    Set<String> teacherIds(String tenantId, LocalDate from, LocalDate to);

    /**
     * Published periods overlapping the days {@code from}..{@code to} of every teacher in {@code teacherUserIds}, in one
     * query for the whole roster (what {@link CalendarReadPort#teacherPeriods} answers for one teacher); teachers with
     * no period are absent. No principal: the caller has already decided the teachers may be read.
     */
    Map<String, List<TeacherPeriod>> teacherPeriods(String tenantId, Collection<String> teacherUserIds, LocalDate from,
            LocalDate to);

    /**
     * One page of the period-by-period sheet (a row per period, not per event): the PUBLISHED events of {@code role}
     * in the range, narrowed in SQL, each expanded to its periods, ordered by period start, school name, class name
     * and period number, then cut to {@code offset}/{@code limit}. {@code total} counts every period of the range.
     */
    PeriodSlice periodSlice(String tenantId, LocalDate from, LocalDate to, String role, String schoolId,
            String teacherUserId, int offset, int limit);

    /** One period of an event; {@code from}/{@code to} split the event's span evenly between its periods. */
    record PeriodRow(TeachingPeriod event, int periodNo, Instant from, Instant to) {}

    record PeriodSlice(List<PeriodRow> rows, int total) {}

    /** {@code role} is {@code MAIN} or {@code ASSISTANT}; names are empty strings when the row is gone. */
    record TeachingPeriod(
            String eventId, String teacherUserId, String teacherName, String role, String classId, String className,
            String subjectId, String subjectName, String schoolId, String schoolName, String levelId, String levelName,
            Instant start, Instant end, int periodStart, int periodCount) {}
}
