package com.erg.ergspringboot.lms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * What the class log needs from the published timetable. Declared here and implemented by the calendar module,
 * because calendar already depends on lms (the other direction would be a module cycle).
 */
public interface LessonScheduleReadPort {

    /**
     * A published, non-cancelled event: {@code periodCount} timetable periods from {@code periodStart} on one day.
     * The detail fields ({@code start} on) are filled by the teacher/class queries; {@code mainTeacherIds} are the
     * teachers with the MAIN role (assistants never keep the class log).
     */
    record LessonSlot(String eventId, String classId, String subjectId, LocalDate date, int periodStart, int periodCount,
                      Instant start, Instant end, String schoolId, String levelId, String session,
                      List<String> mainTeacherIds) {

        public LessonSlot(String eventId, String classId, String subjectId, LocalDate date, int periodStart, int periodCount) {
            this(eventId, classId, subjectId, date, periodStart, periodCount, null, null, null, null, null, List.of());
        }
    }

    /** Slots of the given classes and subjects on the Asia/Ho_Chi_Minh days {@code from}..{@code to}, both inclusive. */
    List<LessonSlot> publishedSlots(
            String tenantId, Collection<String> classIds, Collection<String> subjectIds, LocalDate from, LocalDate to);

    /** Published slots the teacher teaches as MAIN teacher, days {@code from}..{@code to} inclusive. */
    List<LessonSlot> teacherSlots(String tenantId, String teacherUserId, LocalDate from, LocalDate to);

    /** Published slots of one class (any subject), days {@code from}..{@code to} inclusive. */
    List<LessonSlot> classSlots(String tenantId, String classId, LocalDate from, LocalDate to);

    /** Published slots of every class of the tenant (with their MAIN teachers), days {@code from}..{@code to} inclusive. */
    List<LessonSlot> publishedWeekSlots(String tenantId, LocalDate from, LocalDate to);

    /** As above, narrowed in SQL to the given schools (null = all, empty = none) and subject (null = all). */
    List<LessonSlot> publishedWeekSlots(String tenantId, LocalDate from, LocalDate to, Collection<String> schoolIds, String subjectId);
}
