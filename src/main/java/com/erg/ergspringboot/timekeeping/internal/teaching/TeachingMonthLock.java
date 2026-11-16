package com.erg.ergspringboot.timekeeping.internal.teaching;

import com.erg.ergspringboot.lms.LessonLogMonthLock;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingMonthRepository;
import java.time.YearMonth;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tells the class log whether HR closed a teaching-hours month; a closed month freezes its log entries. */
@Component
class TeachingMonthLock implements LessonLogMonthLock {

    private final TkTeachingMonthRepository months;

    TeachingMonthLock(TkTeachingMonthRepository months) {
        this.months = months;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isClosed(String tenantId, YearMonth month) {
        return months.findByTenantIdAndMonth(tenantId, month.toString()).map(m -> "CLOSED".equals(m.getStatus())).orElse(false);
    }
}
