package com.erg.ergspringboot.calendar;

import java.time.Instant;

/**
 * One published teaching period of a teacher, for callers that have no principal (jobs, timekeeping).
 * {@code role} is the teacher's own role on the event: {@code MAIN} or {@code ASSISTANT}.
 */
public record TeacherPeriod(
        Instant start, Instant end, String className, String subject, String room,
        String eventId, String classId, String subjectId, String schoolId, String levelId,
        int periodStart, int periodCount, String role) {

    /** Display-only view (no ids); kept so callers and tests that only need the clock and labels stay short. */
    public TeacherPeriod(Instant start, Instant end, String className, String subject, String room) {
        this(start, end, className, subject, room, null, null, null, null, null, 1, 1, "MAIN");
    }
}
