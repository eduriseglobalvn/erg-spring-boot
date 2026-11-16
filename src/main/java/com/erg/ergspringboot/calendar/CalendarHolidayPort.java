package com.erg.ergspringboot.calendar;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Public boundary the timekeeping module uses to apply a day off to the teaching calendar (KE_HOACH_LICH_DAY.md B11).
 * Every method joins the caller's transaction, so a holiday and the periods it cancels commit together.
 * {@code schoolId} null means every school of the tenant.
 */
public interface CalendarHolidayPort {

    /** The PUBLISHED periods on {@code dates} that already started, with the reported ones (báo giảng) among them. */
    HolidayImpact pastImpact(String tenantId, String schoolId, Collection<LocalDate> dates);

    /** Cancels the PUBLISHED periods on {@code dates} for the holiday and tells their teachers; returns how many. */
    int cancelPeriods(String tenantId, String schoolId, Collection<LocalDate> dates, String holidayId,
            String holidayName, String actorId);

    /** Puts back every period that exactly this holiday cancelled (past ones included); returns how many. */
    int restorePeriods(String tenantId, String holidayId, String actorId);

    /** PUBLISHED periods on each holiday's day plus those it already cancelled, by holiday id. */
    Map<String, Affected> affected(String tenantId, Collection<String> holidayIds);

    /** Published periods and distinct schools per day, for the days-off month view. */
    List<DayLoad> dayLoads(String tenantId, LocalDate from, LocalDate to, Collection<String> schoolIds);

    record Affected(int periods, int schools) {
    }

    record DayLoad(String date, int periods, int schools) {
    }

    /** {@code dates}: only the days that have at least one started period. */
    record HolidayImpact(List<LocalDate> dates, int taughtPeriods, int reportedPeriods, List<SchoolImpact> schools) {
        public HolidayImpact {
            dates = List.copyOf(dates);
            schools = List.copyOf(schools);
        }

        public boolean any() {
            return taughtPeriods > 0;
        }
    }

    record SchoolImpact(String schoolId, String schoolName, int taughtPeriods, int reportedPeriods) {
    }
}
