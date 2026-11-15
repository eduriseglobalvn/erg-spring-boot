package com.erg.ergspringboot.timekeeping.internal.teaching;

import com.erg.ergspringboot.calendar.LessonReportCounter;
import com.erg.ergspringboot.lms.LessonLogReportPort;
import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Answers the calendar's "which of these periods were already reported (bao giang)" from the class log. */
@Component
class LessonReportCounterAdapter implements LessonReportCounter {

    private final LessonLogReportPort classLog;

    LessonReportCounterAdapter(LessonLogReportPort classLog) {
        this.classLog = classLog;
    }

    @Override
    public Set<String> reported(String tenantId, Collection<String> calendarEventIds) {
        return classLog.reportedEvents(tenantId, calendarEventIds);
    }
}
