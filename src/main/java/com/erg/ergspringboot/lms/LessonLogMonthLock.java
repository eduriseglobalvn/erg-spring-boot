package com.erg.ergspringboot.lms;

import java.time.YearMonth;

/** Whether a month of teaching hours is closed (HR closed it); a closed month freezes the class log. Implemented by timekeeping. */
public interface LessonLogMonthLock {

    boolean isClosed(String tenantId, YearMonth month);
}
