package com.erg.ergspringboot.timekeeping.internal.day;

import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.DayStatus;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.PunchDirection;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.internal.rules.DayInput;
import com.erg.ergspringboot.timekeeping.internal.rules.DayResult;
import com.erg.ergspringboot.timekeeping.internal.rules.WorkUnitCalculator;
import com.erg.ergspringboot.timekeeping.internal.schedule.TeacherPeriodReader;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single implementation behind {@code recomputeDay}: turns one person's day into its {@code tk_days} row.
 *
 * <p>Precedence: school or tenant-wide holiday → HOLIDAY; no shift that weekday → OFF; approved full-day LEAVE →
 * LEAVE (ANNUAL / PAID_PERSONAL / UNPAID, 0 units, kind kept in {@code leave_kind} so the sheet shows P vs KP) or
 * SICK; approved BUSINESS → BUSINESS with the shift units; approved AM/PM leave → HALF with half the shift units
 * (half leave + half work, whether or not the person punched); otherwise the punches through
 * {@link WorkUnitCalculator}, where an approved EXPLANATION's {@code proposed_out} stands in for a missing OUT.
 * A closed day without punch or request is ABSENT_UNPAID; an open one gets no row. Only APPROVED requests count.
 *
 * <p>Locked period: {@link #recompute(String, String, LocalDate)} and {@link #closeDay} skip (and log) a day whose
 * month is locked for the school, so a locked sheet is never silently rewritten. An explicit admin recompute after
 * a reopen (T6) calls {@link #recompute(String, String, LocalDate, boolean)} with {@code evenIfLocked = true}.
 * Rejected punches are never stored, so every stored punch counts (FLAGGED / PENDING_REVIEW are flags).
 */
@Service
public class DayAggregationService {

    static final String SELF_PERMISSION = "timekeeping.self";
    private static final Logger log = LoggerFactory.getLogger(DayAggregationService.class);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final TkPunchRepository punches;
    private final TkDayRepository days;
    private final TkHolidayRepository holidays;
    private final TkRequestRepository requests;
    private final WorkContextResolver context;
    private final TeacherPeriodReader periods;
    private final UserRepository users;
    private final Clock clock;

    DayAggregationService(TkPunchRepository punches, TkDayRepository days, TkHolidayRepository holidays,
            TkRequestRepository requests, WorkContextResolver context, TeacherPeriodReader periods, UserRepository users,
            Clock clock) {
        this.punches = punches;
        this.days = days;
        this.holidays = holidays;
        this.requests = requests;
        this.context = context;
        this.periods = periods;
        this.users = users;
        this.clock = clock;
    }

    /** First counted IN and latest counted OUT of the day. */
    public static Optional<TkPunch> firstIn(List<TkPunch> dayPunches) {
        return dayPunches.stream().filter(p -> counts(p) && p.getDirection() == PunchDirection.IN).findFirst();
    }

    public static Optional<TkPunch> latestOut(List<TkPunch> dayPunches) {
        return dayPunches.stream().filter(p -> counts(p) && p.getDirection() == PunchDirection.OUT)
                .reduce((first, second) -> second);
    }

    private static boolean counts(TkPunch p) {
        return p.getStatus() != PunchStatus.REJECTED;
    }

    /** Punch / request path: a day in a locked period is skipped. */
    @Transactional
    public void recompute(String tenantId, String userId, LocalDate date) {
        recompute(tenantId, userId, date, false);
    }

    /** @param evenIfLocked true only for an explicit admin recompute of a reopened or locked period (T6). */
    @Transactional
    public void recompute(String tenantId, String userId, LocalDate date, boolean evenIfLocked) {
        List<TkPunch> dayPunches = punches.findByTenantIdAndUserIdAndWorkDateOrderByServerTimeAscCodeAsc(tenantId, userId, date);
        String schoolId = schoolOf(dayPunches).or(() -> context.schoolOf(tenantId, userId)).orElse(null);
        if (schoolId == null) return;
        SchoolDay school = schoolDay(tenantId, schoolId, date, holidays.findForSchool(tenantId, schoolId, date, date));
        if (school.locked() && !evenIfLocked) {
            log.info("Skipping recompute of a locked day: tenant {} user {} date {}", tenantId, userId, date);
            return;
        }
        Optional<TkDay> existing = days.findByTenantIdAndUserIdAndWorkDate(tenantId, userId, date);
        Optional<TkDay> row = build(school, userId, date, dayPunches,
                requests.findApprovedOnForUser(tenantId, userId, date), existing.orElse(null), closed(school, date));
        if (row.isPresent()) {
            days.save(row.get());
        } else {
            existing.ifPresent(days::delete);
        }
    }

    /**
     * Closes {@code date} for one tenant (the caller sets the tenant context): everyone ACTIVE holding
     * timekeeping.self plus anyone who punched. Reads are bulk (roster, schools, punches, requests, holidays and
     * existing rows once; shift, policy and lock once per school), rows are written in one batch, and one failing
     * person is logged and skipped. Re-running gives the same rows. Returns the number of rows written.
     */
    public int closeDay(String tenantId, LocalDate date) {
        Map<String, List<TkPunch>> punchesByUser = punches.findByTenantIdAndWorkDateOrderByServerTimeAscCodeAsc(tenantId, date)
                .stream().collect(Collectors.groupingBy(TkPunch::getUserId, LinkedHashMap::new, Collectors.toList()));
        Set<String> people = new LinkedHashSet<>(users.findActiveUserIdsHoldingPermission(tenantId, SELF_PERMISSION, date));
        people.addAll(punchesByUser.keySet());
        Map<String, String> schoolOf = context.schoolsOf(tenantId, people);
        Map<String, List<TkRequest>> requestsByUser = requests.findApprovedOn(tenantId, date).stream()
                .collect(Collectors.groupingBy(TkRequest::getUserId));
        Map<String, TkDay> existing = days.findByTenantIdAndWorkDate(tenantId, date).stream()
                .collect(Collectors.toMap(TkDay::getUserId, Function.identity(), (a, b) -> a));
        List<TkHoliday> dayHolidays = holidays.findByTenantIdAndHolidayDate(tenantId, date);
        Map<String, SchoolDay> schools = new HashMap<>();

        List<TkDay> rows = new ArrayList<>();
        for (String userId : people) {
            try {
                List<TkPunch> dayPunches = punchesByUser.getOrDefault(userId, List.of());
                String schoolId = schoolOf(dayPunches).orElse(schoolOf.get(userId));
                if (schoolId == null) continue;
                SchoolDay school = schools.computeIfAbsent(schoolId, id -> schoolDay(tenantId, id, date,
                        dayHolidays.stream().filter(h -> h.getSchoolId() == null || h.getSchoolId().equals(id)).toList()));
                if (school.locked()) continue;
                build(school, userId, date, dayPunches, requestsByUser.getOrDefault(userId, List.of()),
                        existing.get(userId), true).ifPresent(rows::add);
            } catch (RuntimeException ex) {
                log.warn("Daily close failed for tenant {} user {} date {}: {}", tenantId, userId, date,
                        ex.getClass().getSimpleName());
            }
        }
        days.saveAll(rows);
        return rows.size();
    }

    private record SchoolDay(String tenantId, String schoolId, boolean holiday, boolean locked, TkShift shift,
            TkPolicy policy) {}

    private SchoolDay schoolDay(String tenantId, String schoolId, LocalDate date, List<TkHoliday> schoolHolidays) {
        return new SchoolDay(tenantId, schoolId, !schoolHolidays.isEmpty(), context.isLocked(tenantId, schoolId, date),
                context.shiftFor(tenantId, schoolId, date).orElse(null), context.policyFor(tenantId, schoolId, date));
    }

    private static Optional<String> schoolOf(List<TkPunch> dayPunches) {
        return dayPunches.stream().map(TkPunch::getSchoolId).filter(Objects::nonNull).findFirst();
    }

    /** Before today, or more than 60 minutes after the shift end. */
    private boolean closed(SchoolDay school, LocalDate date) {
        Instant now = clock.instant();
        if (date.isBefore(LocalDate.ofInstant(now, WorkContextResolver.ZONE))) return true;
        return school.shift() != null && now.isAfter(
                date.atTime(school.shift().getEndTime()).plusMinutes(60).atZone(WorkContextResolver.ZONE).toInstant());
    }

    private Optional<TkDay> build(SchoolDay school, String userId, LocalDate date, List<TkPunch> dayPunches,
            List<TkRequest> approved, TkDay existing, boolean closed) {
        Optional<TkPunch> in = firstIn(dayPunches);
        Optional<TkPunch> out = latestOut(dayPunches);
        TkShift shift = school.shift();
        TkDay day = existing != null ? existing : new TkDay();
        day.setTenantId(school.tenantId());
        day.setUserId(userId);
        day.setWorkDate(date);
        day.setSchoolId(school.schoolId());
        day.setShiftId(shift == null ? null : shift.getId());
        day.setInPunchId(in.map(TkPunch::getId).orElse(null));
        day.setOutPunchId(out.map(TkPunch::getId).orElse(null));
        day.setRemote(in.map(TkPunch::getRemote).orElse(false) || out.map(TkPunch::getRemote).orElse(false));
        day.setLateMinutes(0);
        day.setEarlyMinutes(0);
        day.setDeductMinutes(0);
        day.setMissing(null);
        day.setLeaveKind(null);

        TkRequest leave = first(approved, RequestType.LEAVE);
        TkRequest business = first(approved, RequestType.BUSINESS);
        if (school.holiday()) {
            set(day, DayStatus.HOLIDAY, BigDecimal.ZERO);
        } else if (shift == null) {
            set(day, DayStatus.OFF, BigDecimal.ZERO);
        } else if (leave != null && leave.getPart() == DayPart.FULL) {
            set(day, leave.getLeaveKind() == LeaveKind.SICK ? DayStatus.SICK : DayStatus.LEAVE, BigDecimal.ZERO);
            day.setLeaveKind(leave.getLeaveKind());
        } else if (business != null) {
            set(day, DayStatus.BUSINESS, shift.getWorkUnits());
        } else if (leave != null) {
            set(day, DayStatus.HALF, shift.getWorkUnits().divide(TWO));
            day.setLeaveKind(leave.getLeaveKind());
        } else {
            Instant outTime = out.map(TkPunch::getServerTime).orElse(null);
            TkRequest explanation = first(approved, RequestType.EXPLANATION);
            if (outTime == null && explanation != null && explanation.getProposedOut() != null) {
                outTime = date.atTime(explanation.getProposedOut()).atZone(WorkContextResolver.ZONE).toInstant();
            }
            if (in.isEmpty() && outTime == null) {
                if (!closed) return Optional.empty();
                set(day, DayStatus.ABSENT_UNPAID, BigDecimal.ZERO);
            } else {
                DayResult result = WorkUnitCalculator.compute(new DayInput(shift.getStartTime(), shift.getEndTime(),
                        shift.getWorkUnits(), in.map(TkPunch::getServerTime).orElse(null), outTime,
                        school.policy().getLateToleranceMin(), school.policy().getLateBlockMin(),
                        WorkContextResolver.ZONE, closed));
                set(day, result.status(), result.workUnits());
                day.setLateMinutes(result.lateMinutes());
                day.setEarlyMinutes(result.earlyMinutes());
                day.setDeductMinutes(result.deductMinutes());
                day.setMissing(result.missing());
            }
        }
        // shortcut: one calendar call per person and day (calendar has no bulk teacherPeriods); add a bulk read
        // to CalendarReadPort when the daily close of a large tenant gets slow.
        // A failed read keeps the row's previous count (0 for a new row); the day itself is still written.
        periods.read(school.tenantId(), userId, date, date).ifPresent(list -> day.setPeriods(list.size()));
        return Optional.of(day);
    }

    private static void set(TkDay day, DayStatus status, BigDecimal units) {
        day.setStatus(status);
        day.setWorkUnits(units.setScale(1, RoundingMode.HALF_UP));
    }

    /** Full-day requests before half days; ties keep the query order (oldest first). */
    private static TkRequest first(List<TkRequest> approved, RequestType type) {
        return approved.stream().filter(r -> r.getType() == type)
                .min(Comparator.comparing((TkRequest r) -> r.getPart() != DayPart.FULL)).orElse(null);
    }
}
