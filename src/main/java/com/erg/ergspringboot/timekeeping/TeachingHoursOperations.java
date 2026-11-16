package com.erg.ergspringboot.timekeeping;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.TeachingViews.ConfirmTeachingCommand;
import com.erg.ergspringboot.timekeeping.TeachingViews.MyTeachingMonth;
import com.erg.ergspringboot.timekeeping.TeachingViews.ReportTeachingIssueCommand;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingConfirmationView;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingExport;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingMonthSheet;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingPeriodPage;
import com.erg.ergspringboot.timekeeping.internal.teaching.TeachingHoursService;
import com.erg.ergspringboot.timekeeping.internal.teaching.TeachingMonthService;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;

/**
 * Boundary of the teaching-hours (cong tiet day) API: the caller comes from the security context, the month is parsed
 * here and each sheet read first sends back to PENDING any teacher whose confirmed totals the timetable has since moved.
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
        months.recheck(principal.tenantId(), parsed);
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
        return hours.periods(principal.tenantId(), role, from, to, schoolId, teacherId, q, page);
    }

    public TeachingMonthSheet monthSheet(ErgPrincipal principal, String month, String role) {
        YearMonth parsed = TeachingHoursService.parse(month);
        months.recheck(principal.tenantId(), parsed);
        return hours.monthSheet(principal.tenantId(), parsed, role);
    }

    public TeachingMonthSheet close(ErgPrincipal principal, String month) {
        YearMonth parsed = TeachingHoursService.parse(month);
        months.close(principal.tenantId(), principal.userId(), parsed);
        return hours.monthSheet(principal.tenantId(), parsed, "MAIN");
    }

    public TeachingExport export(ErgPrincipal principal, int year) {
        return hours.export(principal.tenantId(), year);
    }
}
