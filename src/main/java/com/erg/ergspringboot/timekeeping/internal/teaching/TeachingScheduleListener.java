package com.erg.ergspringboot.timekeeping.internal.teaching;

import com.erg.ergspringboot.calendar.CalendarBatchCancelled;
import com.erg.ergspringboot.calendar.CalendarBatchPublished;
import com.erg.ergspringboot.calendar.CalendarHolidayCancelled;
import com.erg.ergspringboot.calendar.CalendarSlotsChanged;
import com.erg.ergspringboot.platform.security.TenantContext;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A timetable batch was published or cancelled: teachers who already confirmed a month whose totals just moved go back
 * to PENDING. Edits of room or note (CalendarEventChanged) never change a period count, so they are not heard.
 * A holiday cancelling periods (CalendarHolidayCancelled) moves the totals the same way.
 */
@Component
class TeachingScheduleListener {

    private final TeachingMonthService months;

    TeachingScheduleListener(TeachingMonthService months) {
        this.months = months;
    }

    @ApplicationModuleListener
    void on(CalendarBatchPublished event) {
        recheck(event.tenantId());
    }

    @ApplicationModuleListener
    void on(CalendarBatchCancelled event) {
        recheck(event.tenantId());
    }

    /** A swap moves periods between teachers and an edit between days or months: totals move like a publish. */
    @ApplicationModuleListener
    void on(CalendarSlotsChanged event) {
        recheck(event.tenantId());
    }

    @ApplicationModuleListener
    void on(CalendarHolidayCancelled event) {
        recheck(event.tenantId());
    }

    private void recheck(String tenantId) {
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(tenantId, null, "system"))) {
            months.recheckOpenMonths(tenantId);
        }
    }
}
