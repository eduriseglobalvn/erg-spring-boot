package com.erg.ergspringboot.timekeeping.internal.request;

import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.internal.rules.LeaveDayCounter;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkHoliday;
import com.erg.ergspringboot.timekeeping.persistence.TkHolidayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkRequest;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.users.persistence.EmployeeProfile;
import com.erg.ergspringboot.users.persistence.EmployeeProfileRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Annual leave: entitlement = policy {@code annual_leave_days} + one day per full {@code seniority_step_years},
 * the years counted whole from the join date ({@code EmployeeProfile.joinedAt}) to 31/12 of the year, so a join on
 * 01/08/2016 gives 14 for 2026. No join date: base days only; joined after that 31/12: 0 (no proration).
 * Used = APPROVED ANNUAL leave inside the year; a leave across year-end is recounted per year on its overlap
 * (school workdays, holidays). The policy is the one in force on 31/12 of the year.
 */
@Service
public class LeaveBalanceService {

    private final TkRequestRepository requests;
    private final TkHolidayRepository holidays;
    private final WorkContextResolver context;
    private final EmployeeProfileRepository profiles;

    LeaveBalanceService(TkRequestRepository requests, TkHolidayRepository holidays, WorkContextResolver context,
            EmployeeProfileRepository profiles) {
        this.requests = requests;
        this.holidays = holidays;
        this.context = context;
        this.profiles = profiles;
    }

    public BigDecimal annualBalance(String tenantId, String userId, int year) {
        LocalDate first = LocalDate.of(year, 1, 1);
        LocalDate last = LocalDate.of(year, 12, 31);
        BigDecimal used = requests.findApprovedAnnualLeaveOverlapping(tenantId, userId, first, last).stream()
                .map(r -> daysInside(r, first, last))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return entitlement(tenantId, userId, year).subtract(used);
    }

    public BigDecimal entitlement(String tenantId, String userId, int year) {
        TkPolicy policy = context.policyFor(tenantId, context.schoolOf(tenantId, userId).orElse(""),
                LocalDate.of(year, 12, 31));
        LocalDate joinedAt = profiles.findByUserIdAndTenantId(userId, tenantId).map(EmployeeProfile::getJoinedAt)
                .orElse(null);
        return BigDecimal.valueOf(entitlement(policy.getAnnualLeaveDays(), policy.getSeniorityStepYears(), joinedAt, year));
    }

    static int entitlement(int baseDays, int stepYears, LocalDate joinedAt, int year) {
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        if (joinedAt == null) return baseDays;
        if (joinedAt.isAfter(yearEnd)) return 0;
        int completedYears = Period.between(joinedAt, yearEnd).getYears();
        return baseDays + (stepYears > 0 ? completedYears / stepYears : 0);
    }

    private BigDecimal daysInside(TkRequest leave, LocalDate first, LocalDate last) {
        if (!leave.getFromDate().isBefore(first) && !leave.getToDate().isAfter(last) && leave.getDays() != null) {
            return leave.getDays();
        }
        LocalDate from = leave.getFromDate().isBefore(first) ? first : leave.getFromDate();
        LocalDate to = leave.getToDate().isAfter(last) ? last : leave.getToDate();
        Set<DayOfWeek> workdays = context.shiftsOf(leave.getTenantId(), leave.getSchoolId()).stream()
                .flatMap(shift -> Arrays.stream(shift.getWeekdays())).map(DayOfWeek::of).collect(Collectors.toSet());
        Set<LocalDate> holidayDates = holidays.findForSchool(leave.getTenantId(), leave.getSchoolId(), from, to).stream()
                .map(TkHoliday::getHolidayDate).collect(Collectors.toSet());
        DayPart part = from.equals(to) ? leave.getPart() : DayPart.FULL;
        return LeaveDayCounter.count(from, to, part, workdays, holidayDates);
    }
}
