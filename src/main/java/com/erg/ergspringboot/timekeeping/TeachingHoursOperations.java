package com.erg.ergspringboot.timekeeping;

import com.erg.ergspringboot.calendar.TeachingPeriodReadPort.TeachingPeriod;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.TeachingViews.ConfirmTeachingCommand;
import com.erg.ergspringboot.timekeeping.TeachingViews.MyTeachingMonth;
import com.erg.ergspringboot.timekeeping.TeachingViews.ReportTeachingIssueCommand;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingConfirmationView;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingExport;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingMonthSheet;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingPeriodPage;
import com.erg.ergspringboot.timekeeping.internal.SchoolScope;
import com.erg.ergspringboot.timekeeping.internal.teaching.TeachingHoursService;
import com.erg.ergspringboot.timekeeping.internal.teaching.TeachingMonthService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Boundary of the teaching-hours (cong tiet day) API: the caller comes from the security context, the month is parsed
 * here and a sheet read first sends back to PENDING the teachers whose confirmed totals the timetable has since moved
 * (the caller's own on their sheet, any stale one on the admin sheet).
 */
@Service
public class TeachingHoursOperations {

    private final TeachingHoursService hours;
    private final TeachingMonthService months;

    TeachingHoursOperations(TeachingHoursService hours, TeachingMonthService months) {
        this.hours = hours;
        this.months = months;
    }

    public MyTeachingMonth myMonth(ErgPrincipal principal, String month) {
        YearMonth parsed = TeachingHoursService.parse(month);
        months.recheckMine(principal.tenantId(), principal.userId(), parsed); // only the caller's own confirmation
        return hours.my(principal.tenantId(), principal.userId(), parsed);
    }

    public TeachingPeriodPage myPeriods(ErgPrincipal principal, String month, String role, int page) {
        return hours.myPeriods(principal.tenantId(), principal.userId(), TeachingHoursService.parse(month), role, page);
    }

    public TeachingConfirmationView confirm(ErgPrincipal principal, String month, ConfirmTeachingCommand command) {
        return months.confirm(principal.tenantId(), principal.userId(), TeachingHoursService.parse(month), command);
    }

    public TeachingConfirmationView reportIssue(ErgPrincipal principal, String month, ReportTeachingIssueCommand command) {
        return months.reportIssue(principal.tenantId(), principal.userId(), TeachingHoursService.parse(month), command);
    }

    public TeachingPeriodPage periods(ErgPrincipal principal, String role, LocalDate from, LocalDate to, String schoolId,
            String teacherId, String q, int page) {
        return hours.periods(principal.tenantId(), SchoolScope.of(principal), role, from, to, schoolId, teacherId, q, page);
    }

    public TeachingMonthSheet monthSheet(ErgPrincipal principal, String month, String role) {
        SchoolScope scope = SchoolScope.of(principal);
        YearMonth parsed = TeachingHoursService.parse(month);
        // one read of the month feeds both the check and the sheet; a confirmation is rewritten only when it is stale
        List<TeachingPeriod> events = hours.openMonthEvents(principal.tenantId(), parsed);
        // the recheck is tenant-wide housekeeping (it exposes nothing); only the sheet below is narrowed to the scope
        if (events != null) months.recheck(principal.tenantId(), parsed, TeachingHoursService.totals(events));
        return hours.monthSheet(principal.tenantId(), scope, parsed, role, events);
    }

    /** Closing a month freezes every school (HR, tenant-wide by design); the sheet sent back is narrowed to the scope. */
    public TeachingMonthSheet close(ErgPrincipal principal, String month) {
        SchoolScope scope = SchoolScope.of(principal);
        YearMonth parsed = TeachingHoursService.parse(month);
        months.close(principal.tenantId(), principal.userId(), parsed);
        return hours.monthSheet(principal.tenantId(), scope, parsed, "MAIN", null);
    }

    public TeachingExport export(ErgPrincipal principal, int year, String schoolId, Integer month) {
        return hours.export(principal.tenantId(), SchoolScope.of(principal), year, schoolId, month);
    }
}
