package com.erg.ergspringboot.calendar;

import java.util.Collection;
import java.util.Set;

/**
 * Optional hook: which of these periods already have a submitted lesson report (báo giảng). The calendar knows
 * nothing about reports; the module that owns them provides this bean. Without one nothing counts as reported.
 */
public interface LessonReportCounter {

    /** The subset of {@code calendarEventIds} that has a submitted report. */
    Set<String> reported(String tenantId, Collection<String> calendarEventIds);
}
