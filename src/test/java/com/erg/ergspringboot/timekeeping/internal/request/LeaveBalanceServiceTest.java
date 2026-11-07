package com.erg.ergspringboot.timekeeping.internal.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.RequestStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkHoliday;
import com.erg.ergspringboot.timekeeping.persistence.TkHolidayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkRequest;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.users.persistence.EmployeeProfile;
import com.erg.ergspringboot.users.persistence.EmployeeProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeaveBalanceServiceTest {

    private static final String T = "tenant-a";
    private static final String U = "teacher-1";
    private static final String SCHOOL = "school-1";

    private final TkRequestRepository requests = mock(TkRequestRepository.class);
    private final TkHolidayRepository holidays = mock(TkHolidayRepository.class);
    private final WorkContextResolver context = mock(WorkContextResolver.class);
    private final EmployeeProfileRepository profiles = mock(EmployeeProfileRepository.class);
    private final LeaveBalanceService service = new LeaveBalanceService(requests, holidays, context, profiles);

    @BeforeEach
    void wire() {
        when(context.schoolOf(T, U)).thenReturn(Optional.of(SCHOOL));
        when(context.policyFor(eq(T), eq(SCHOOL), any())).thenReturn(new TkPolicy());
        when(context.shiftsOf(T, SCHOOL)).thenReturn(List.of(weekdayShift()));
        when(requests.findApprovedAnnualLeaveOverlapping(eq(T), eq(U), any(), any())).thenReturn(List.of());
        when(holidays.findForSchool(eq(T), eq(SCHOOL), any(), any())).thenReturn(List.of());
    }

    @Test
    void seniorityIsCountedInWholeYearsToTheEndOfTheYear() {
        assertThat(LeaveBalanceService.entitlement(12, 5, LocalDate.of(2016, 8, 1), 2026)).isEqualTo(14);
        assertThat(LeaveBalanceService.entitlement(12, 5, LocalDate.of(2025, 8, 1), 2026)).isEqualTo(12);
        assertThat(LeaveBalanceService.entitlement(12, 5, LocalDate.of(2021, 12, 31), 2026)).isEqualTo(13);
        assertThat(LeaveBalanceService.entitlement(12, 5, LocalDate.of(2027, 1, 1), 2026)).isZero();
        assertThat(LeaveBalanceService.entitlement(12, 5, null, 2026)).isEqualTo(12);
    }

    @Test
    void joinedAugust2016WithFiveAndAHalfApprovedDaysHasEightAndAHalfLeft() {
        joined(LocalDate.of(2016, 8, 1));
        when(requests.findApprovedAnnualLeaveOverlapping(T, U, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(leave("2026-03-02", "2026-03-06", "5.0"), leave("2026-04-07", "2026-04-07", "0.5")));

        assertThat(service.entitlement(T, U, 2026)).isEqualByComparingTo("14");
        assertThat(service.annualBalance(T, U, 2026)).isEqualByComparingTo("8.5");
    }

    @Test
    void noEmployeeProfileMeansBaseDaysOnly() {
        when(profiles.findByUserIdAndTenantId(U, T)).thenReturn(Optional.empty());

        assertThat(service.annualBalance(T, U, 2026)).isEqualByComparingTo("12");
    }

    @Test
    void leaveSpanningYearEndCountsEachPartInItsOwnYear() {
        // Mon 28/12/2026 .. Fri 08/01/2027 = 4 workdays in 2026; in 2027 the 01/01 holiday leaves Mon 04..Fri 08 = 5.
        TkRequest span = leave("2026-12-28", "2027-01-08", "9.0");
        when(requests.findApprovedAnnualLeaveOverlapping(eq(T), eq(U), any(), any())).thenReturn(List.of(span));
        TkHoliday newYear = new TkHoliday();
        newYear.setHolidayDate(LocalDate.of(2027, 1, 1));
        when(holidays.findForSchool(T, SCHOOL, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 8)))
                .thenReturn(List.of(newYear));

        assertThat(service.annualBalance(T, U, 2026)).isEqualByComparingTo("8"); // 12 - 4
        assertThat(service.annualBalance(T, U, 2027)).isEqualByComparingTo("7"); // 12 - 5
    }

    private void joined(LocalDate date) {
        EmployeeProfile profile = new EmployeeProfile();
        profile.setJoinedAt(date);
        when(profiles.findByUserIdAndTenantId(U, T)).thenReturn(Optional.of(profile));
    }

    private static TkRequest leave(String from, String to, String days) {
        TkRequest r = new TkRequest();
        r.setTenantId(T);
        r.setUserId(U);
        r.setSchoolId(SCHOOL);
        r.setType(RequestType.LEAVE);
        r.setLeaveKind(LeaveKind.ANNUAL);
        r.setStatus(RequestStatus.APPROVED);
        r.setFromDate(LocalDate.parse(from));
        r.setToDate(LocalDate.parse(to));
        r.setPart(from.equals(to) && days.equals("0.5") ? DayPart.AM : DayPart.FULL);
        r.setDays(new BigDecimal(days));
        return r;
    }

    private static TkShift weekdayShift() {
        TkShift s = new TkShift();
        s.setWeekdays(new Integer[] {1, 2, 3, 4, 5});
        return s;
    }
}
