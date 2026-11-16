package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.lms.LessonScheduleReadPort.LessonSlot;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** What the teaching-hours sheets (timekeeping) read from the class log: lesson titles and who has reported. */
public interface LessonLogReportPort {

    /** One timetable period: its lesson title (actual period, else PPCT), whether it ended and whether it is confirmed. */
    record PeriodState(String eventId, int periodNo, String lessonTitle, boolean happened, boolean confirmed) {}

    /** One state per period of every slot (a slot of 2 periods gives 2 states). */
    List<PeriodState> periodStates(String tenantId, Collection<LessonSlot> slots, Instant now);

    /** The events that have at least one confirmed class-log entry (a submitted report, bao giang). */
    Set<String> reportedEvents(String tenantId, Collection<String> eventIds);
}
