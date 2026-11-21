package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkDay;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPeriodLockRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;

/** The two checks every write of a day off or a Saturday mode shares: the period is open, and stored days are rebuilt. */
@Component
class DayOffSupport {

    private final WorkContextResolver context;
    private final TkPeriodLockRepository locks;
    private final TkDayRepository dayRows;
    private final DayAggregationService aggregation;
    private final AdminSupport support;

    DayOffSupport(WorkContextResolver context, TkPeriodLockRepository locks, TkDayRepository dayRows,
            DayAggregationService aggregation, AdminSupport support) {
        this.context = context;
        this.locks = locks;
        this.dayRows = dayRows;
        this.aggregation = aggregation;
        this.support = support;
    }

    /** 409 PERIOD_LOCKED when the month is locked for the school, or (system-wide row) for any school. */
    void requireOpen(String tenantId, String schoolId, LocalDate date) {
        if (schoolId != null) {
            context.requireOpen(tenantId, schoolId, date);
        } else if (locks.existsByTenantIdAndPeriodAndReopenedAtIsNull(tenantId, YearMonth.from(date).toString())) {
            throw WorkContextResolver.periodLocked();
        }
    }

    /**
     * A change on a past or current day rebuilds the stored day rows it touches (the school, or every school), but only
     * the ones the change can move: a person with a shift and a counted punch is worked out as on a working day whether
     * or not it is a day off, so that row stays. The rest are rebuilt together, synchronously.
     */
    void recomputeStoredDays(String tenantId, String schoolId, LocalDate date) {
        if (date.isAfter(support.today())) return;
        List<String> affected = dayRows.findByTenantIdAndWorkDate(tenantId, date).stream()
                .filter(d -> schoolId == null || schoolId.equals(d.getSchoolId()))
                .filter(d -> d.getShiftId() == null || d.getInPunchId() == null && d.getOutPunchId() == null)
                .map(TkDay::getUserId).distinct().toList();
        aggregation.recomputeMany(tenantId, date, affected);
    }
}
