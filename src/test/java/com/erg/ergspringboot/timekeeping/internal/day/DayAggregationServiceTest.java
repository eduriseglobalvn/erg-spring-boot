package com.erg.ergspringboot.timekeeping.internal.day;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.DayStatus;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.Missing;
import com.erg.ergspringboot.timekeeping.PunchDirection;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.RequestStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkDay;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkHoliday;
import com.erg.ergspringboot.timekeeping.persistence.TkHolidayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkPunch;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequest;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.users.persistence.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DayAggregationServiceTest {

    private static final String T = "tenant-a";
    private static final String U = "teacher-1";
    private static final String U2 = "teacher-2";
    private static final String SCHOOL = "school-1";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12); // Thursday
    private static final Instant NEXT_MORNING = Instant.parse("2026-11-13T02:00:00Z");

    private final TkPunchRepository punches = mock(TkPunchRepository.class);
    private final TkDayRepository days = mock(TkDayRepository.class);
    private final TkHolidayRepository holidays = mock(TkHolidayRepository.class);
    private final TkRequestRepository requests = mock(TkRequestRepository.class);
    private final WorkContextResolver context = mock(WorkContextResolver.class);
    private final CalendarReadPort calendar = mock(CalendarReadPort.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TkShift shift = shift();

    @BeforeEach
    void wire() {
        when(context.schoolOf(T, U)).thenReturn(Optional.of(SCHOOL));
        when(context.shiftFor(eq(T), eq(SCHOOL), any())).thenReturn(Optional.of(shift));
        when(context.policyFor(eq(T), eq(SCHOOL), any())).thenReturn(new TkPolicy());
        when(days.findByTenantIdAndUserIdAndWorkDate(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void approvedAnnualLeaveIsLeaveWithZeroUnitsAndPaidKind() {
        approved(leave(LeaveKind.ANNUAL, DayPart.FULL));

        TkDay day = recompute();

        assertThat(day.getStatus()).isEqualTo(DayStatus.LEAVE);
        assertThat(day.getWorkUnits()).isEqualByComparingTo("0");
        assertThat(day.getLeaveKind()).isEqualTo(LeaveKind.ANNUAL);
    }

    @Test
    void approvedUnpaidLeaveIsLeaveNotAbsent() {
        approved(leave(LeaveKind.UNPAID, DayPart.FULL));

        TkDay day = recompute();

        assertThat(day.getStatus()).isEqualTo(DayStatus.LEAVE);
        assertThat(day.getLeaveKind()).isEqualTo(LeaveKind.UNPAID);
    }

    @Test
    void sickLeaveIsSick() {
        approved(leave(LeaveKind.SICK, DayPart.FULL));

        assertThat(recompute().getStatus()).isEqualTo(DayStatus.SICK);
    }

    @Test
    void businessTripIsOneUnit() {
        TkRequest trip = request(RequestType.BUSINESS);
        approved(trip);

        TkDay day = recompute();

        assertThat(day.getStatus()).isEqualTo(DayStatus.BUSINESS);
        assertThat(day.getWorkUnits()).isEqualByComparingTo("1.0");
    }

    @Test
    void halfDayLeaveIsHalfWithHalfAUnitEvenWhenPunched() {
        approved(leave(LeaveKind.ANNUAL, DayPart.AM));
        punched(punch(PunchDirection.IN, "2026-11-12T05:00:00Z"), punch(PunchDirection.OUT, "2026-11-12T08:00:00Z"));

        TkDay day = recompute();

        assertThat(day.getStatus()).isEqualTo(DayStatus.HALF);
        assertThat(day.getWorkUnits()).isEqualByComparingTo("0.5");
        assertThat(day.getWorkUnits().scale()).isEqualTo(1);
    }

    @Test
    void holidayWinsOverPunchesAndLeave() {
        when(holidays.findForSchool(T, SCHOOL, DAY, DAY)).thenReturn(List.of(holiday(null)));
        approved(leave(LeaveKind.ANNUAL, DayPart.FULL));
        punched(punch(PunchDirection.IN, "2026-11-12T00:25:00Z"));

        TkDay day = recompute();

        assertThat(day.getStatus()).isEqualTo(DayStatus.HOLIDAY);
        assertThat(day.getWorkUnits()).isEqualByComparingTo("0");
        assertThat(day.getLeaveKind()).isNull();
    }

    @Test
    void weekdayWithoutShiftIsOff() {
        when(context.shiftFor(eq(T), eq(SCHOOL), any())).thenReturn(Optional.empty());
        approved(leave(LeaveKind.ANNUAL, DayPart.FULL));

        TkDay day = recompute();

        assertThat(day.getStatus()).isEqualTo(DayStatus.OFF);
        assertThat(day.getShiftId()).isNull();
    }

    @Test
    void approvedExplanationProposedOutTurnsMissingOutIntoFull() {
        punched(punch(PunchDirection.IN, "2026-11-12T00:25:00Z"));
        TkRequest explanation = request(RequestType.EXPLANATION);
        explanation.setFromDate(null);
        explanation.setToDate(null);
        explanation.setWorkDate(DAY);
        explanation.setProposedOut(LocalTime.of(11, 30));
        approved(explanation);

        TkDay day = recompute();

        assertThat(day.getStatus()).isEqualTo(DayStatus.FULL);
        assertThat(day.getWorkUnits()).isEqualByComparingTo("1.0");
        assertThat(day.getMissing()).isNull();
    }

    @Test
    void withoutTheExplanationTheSameDayIsMissingOut() {
        punched(punch(PunchDirection.IN, "2026-11-12T00:25:00Z"));

        TkDay day = recompute();

        assertThat(day.getStatus()).isEqualTo(DayStatus.MISSING_PUNCH);
        assertThat(day.getMissing()).isEqualTo(Missing.OUT);
        assertThat(day.getWorkUnits()).isEqualByComparingTo("0.5");
    }

    @Test
    void periodsAreTheTeachersPeriodsOfTheDay() {
        punched(punch(PunchDirection.IN, "2026-11-12T00:25:00Z"), punch(PunchDirection.OUT, "2026-11-12T04:31:00Z"));
        when(calendar.teacherPeriods(T, U, DAY, DAY)).thenReturn(List.of(period(1), period(2)));

        TkDay day = recompute();

        assertThat(day.getPeriods()).isEqualTo(2);
        assertThat(day.getStatus()).isEqualTo(DayStatus.FULL);
        assertThat(day.getInPunchId()).isEqualTo("p-IN");
        assertThat(day.getOutPunchId()).isEqualTo("p-OUT");
    }

    @Test
    void lockedPeriodIsNotRewrittenUnlessExplicitlyAsked() {
        punched(punch(PunchDirection.IN, "2026-11-12T00:25:00Z"));
        when(context.isLocked(T, SCHOOL, DAY)).thenReturn(true);

        service(NEXT_MORNING).recompute(T, U, DAY);
        verify(days, never()).save(any());

        service(NEXT_MORNING).recompute(T, U, DAY, true);
        verify(days).save(any());
    }

    @Test
    void openDayWithoutPunchOrRequestWritesNothingAndDropsAStaleRow() {
        TkDay stale = new TkDay();
        when(days.findByTenantIdAndUserIdAndWorkDate(T, U, DAY)).thenReturn(Optional.of(stale));

        service(Instant.parse("2026-11-12T01:00:00Z")).recompute(T, U, DAY);

        verify(days, never()).save(any());
        verify(days).delete(stale);
    }

    @Test
    void existingRowIsUpdatedNotDuplicated() {
        TkDay existing = new TkDay();
        existing.setId("day-1");
        existing.setLeaveKind(LeaveKind.ANNUAL);
        when(days.findByTenantIdAndUserIdAndWorkDate(T, U, DAY)).thenReturn(Optional.of(existing));
        punched(punch(PunchDirection.IN, "2026-11-12T00:25:00Z"), punch(PunchDirection.OUT, "2026-11-12T04:31:00Z"));

        TkDay day = recompute();

        assertThat(day).isSameAs(existing);
        assertThat(day.getLeaveKind()).isNull();
    }

    // ---- daily close (bulk) ----

    @Test
    void closeMarksNoPunchNoRequestAsAbsentAndPunchedDaysFromThePunches() {
        when(users.findActiveUserIdsHoldingPermission(T, "timekeeping.self")).thenReturn(List.of(U, U2));
        when(context.schoolsOf(eq(T), anyCollection())).thenReturn(Map.of(U, SCHOOL, U2, SCHOOL));
        when(punches.findByTenantIdAndWorkDateOrderByServerTimeAscCodeAsc(T, DAY)).thenReturn(List.of(
                punch(U2, PunchDirection.IN, "2026-11-12T00:25:00Z"), punch(U2, PunchDirection.OUT, "2026-11-12T04:31:00Z")));

        Map<String, TkDay> rows = close();

        assertThat(rows.get(U).getStatus()).isEqualTo(DayStatus.ABSENT_UNPAID);
        assertThat(rows.get(U).getWorkUnits()).isEqualByComparingTo("0");
        assertThat(rows.get(U).getMissing()).isNull();
        assertThat(rows.get(U2).getStatus()).isEqualTo(DayStatus.FULL);
    }

    @Test
    void closeAppliesTenantWideHolidaysButNotAnotherSchoolsHoliday() {
        when(users.findActiveUserIdsHoldingPermission(T, "timekeeping.self")).thenReturn(List.of(U, U2));
        when(context.schoolsOf(eq(T), anyCollection())).thenReturn(Map.of(U, SCHOOL, U2, "school-2"));
        when(context.shiftFor(eq(T), eq("school-2"), any())).thenReturn(Optional.of(shift));
        when(context.policyFor(eq(T), eq("school-2"), any())).thenReturn(new TkPolicy());
        when(holidays.findByTenantIdAndHolidayDate(T, DAY)).thenReturn(List.of(holiday("school-2")));

        Map<String, TkDay> rows = close();

        assertThat(rows.get(U).getStatus()).isEqualTo(DayStatus.ABSENT_UNPAID);
        assertThat(rows.get(U2).getStatus()).isEqualTo(DayStatus.HOLIDAY);

        when(holidays.findByTenantIdAndHolidayDate(T, DAY)).thenReturn(List.of(holiday(null)));
        rows = close();
        assertThat(rows.get(U).getStatus()).isEqualTo(DayStatus.HOLIDAY);
    }

    @Test
    void closeSkipsALockedSchoolAndOneFailingPersonDoesNotStopTheOthers() {
        when(users.findActiveUserIdsHoldingPermission(T, "timekeeping.self")).thenReturn(List.of(U, U2, "teacher-3"));
        when(context.schoolsOf(eq(T), anyCollection()))
                .thenReturn(Map.of(U, SCHOOL, U2, SCHOOL, "teacher-3", "school-locked"));
        when(context.isLocked(T, "school-locked", DAY)).thenReturn(true);
        when(calendar.teacherPeriods(T, U, DAY, DAY)).thenThrow(new IllegalStateException("calendar down"));

        Map<String, TkDay> rows = close();

        assertThat(rows).containsOnlyKeys(U2);
    }

    @Test
    void closeUsesApprovedRequestsLoadedOnceForTheTenant() {
        when(users.findActiveUserIdsHoldingPermission(T, "timekeeping.self")).thenReturn(List.of(U, U2));
        when(context.schoolsOf(eq(T), anyCollection())).thenReturn(Map.of(U, SCHOOL, U2, SCHOOL));
        TkRequest sick = leave(LeaveKind.SICK, DayPart.FULL);
        when(requests.findApprovedOn(T, DAY)).thenReturn(List.of(sick));

        Map<String, TkDay> rows = close();

        assertThat(rows.get(U).getStatus()).isEqualTo(DayStatus.SICK);
        assertThat(rows.get(U2).getStatus()).isEqualTo(DayStatus.ABSENT_UNPAID);
        verify(requests, never()).findApprovedOnForUser(any(), any(), any());
    }

    // ---- helpers ----

    private TkDay recompute() {
        service(NEXT_MORNING).recompute(T, U, DAY);
        ArgumentCaptor<TkDay> saved = ArgumentCaptor.forClass(TkDay.class);
        verify(days).save(saved.capture());
        TkDay day = saved.getValue();
        assertThat(day.getTenantId()).isEqualTo(T);
        assertThat(day.getUserId()).isEqualTo(U);
        assertThat(day.getWorkDate()).isEqualTo(DAY);
        assertThat(day.getSchoolId()).isEqualTo(SCHOOL);
        return day;
    }

    @SuppressWarnings("unchecked")
    private Map<String, TkDay> close() {
        ArgumentCaptor<List<TkDay>> saved = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.clearInvocations(days);
        service(NEXT_MORNING).closeDay(T, DAY);
        verify(days).saveAll(saved.capture());
        Map<String, TkDay> byUser = new java.util.HashMap<>();
        saved.getValue().forEach(d -> byUser.put(d.getUserId(), d));
        return byUser;
    }

    private DayAggregationService service(Instant now) {
        return new DayAggregationService(punches, days, holidays, requests, context, calendar, users,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void approved(TkRequest... approvedRequests) {
        when(requests.findApprovedOnForUser(T, U, DAY)).thenReturn(List.of(approvedRequests));
    }

    private void punched(TkPunch... dayPunches) {
        when(punches.findByTenantIdAndUserIdAndWorkDateOrderByServerTimeAscCodeAsc(T, U, DAY))
                .thenReturn(new ArrayList<>(List.of(dayPunches)));
    }

    private static TkRequest leave(LeaveKind kind, DayPart part) {
        TkRequest r = request(RequestType.LEAVE);
        r.setLeaveKind(kind);
        r.setPart(part);
        return r;
    }

    private static TkRequest request(RequestType type) {
        TkRequest r = new TkRequest();
        r.setTenantId(T);
        r.setUserId(U);
        r.setSchoolId(SCHOOL);
        r.setType(type);
        r.setFromDate(DAY);
        r.setToDate(DAY);
        r.setStatus(RequestStatus.APPROVED);
        return r;
    }

    private static TkPunch punch(PunchDirection direction, String at) {
        return punch(U, direction, at);
    }

    private static TkPunch punch(String userId, PunchDirection direction, String at) {
        TkPunch p = new TkPunch();
        p.setId("p-" + direction);
        p.setTenantId(T);
        p.setUserId(userId);
        p.setSchoolId(SCHOOL);
        p.setShiftId("shift-1");
        p.setWorkDate(DAY);
        p.setDirection(direction);
        p.setServerTime(Instant.parse(at));
        p.setStatus(PunchStatus.VALID);
        return p;
    }

    private static TkHoliday holiday(String schoolId) {
        TkHoliday h = new TkHoliday();
        h.setTenantId(T);
        h.setSchoolId(schoolId);
        h.setHolidayDate(DAY);
        h.setName("Lễ");
        return h;
    }

    private static TeacherPeriod period(int n) {
        Instant start = Instant.parse("2026-11-12T01:00:00Z").plusSeconds(n * 3600L);
        return new TeacherPeriod(start, start.plusSeconds(2700), "7A", "Toán", "P20" + n);
    }

    private static TkShift shift() {
        TkShift s = new TkShift();
        s.setId("shift-1");
        s.setTenantId(T);
        s.setSchoolId(SCHOOL);
        s.setName("Ca sáng");
        s.setStartTime(LocalTime.of(7, 30));
        s.setEndTime(LocalTime.of(11, 30));
        s.setWorkUnits(new BigDecimal("1.0"));
        s.setWeekdays(new Integer[] {1, 2, 3, 4, 5});
        return s;
    }
}
