package com.erg.ergspringboot.timekeeping.internal.day;

import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.DayStatus;
import com.erg.ergspringboot.timekeeping.HolidayPayMode;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.PunchDirection;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.SaturdayMode;
import com.erg.ergspringboot.timekeeping.internal.request.LeaveBalanceService;
import com.erg.ergspringboot.timekeeping.internal.rules.DayInput;
import com.erg.ergspringboot.timekeeping.internal.rules.DayResult;
import com.erg.ergspringboot.timekeeping.internal.rules.WorkUnitCalculator;
import com.erg.ergspringboot.timekeeping.internal.schedule.SaturdayModeResolver;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single implementation behind {@code recomputeDay}: turns one person's day into its {@code tk_days} row.
 *
 * <p>Precedence: a day off (school or tenant-wide holiday, or a Saturday set to a paid / unpaid day off) when the
 * person has no real punch that day → paid HOLIDAY with the shift's units (L, 1 công), unpaid UNPAID_OFF (KL), or
 * annual LEAVE (P) while the balance lasts; a day off on a day without a shift stays HOLIDAY with 0 công (never
 * paid twice); someone who punched on a day off is worked out as on a working day. Then: no shift that weekday → OFF; approved full-day LEAVE →
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
    private final SaturdayModeResolver saturdays;
    private final LeaveBalanceService balances;
    private final Clock clock;

    DayAggregationService(TkPunchRepository punches, TkDayRepository days, TkHolidayRepository holidays,
            TkRequestRepository requests, WorkContextResolver context, TeacherPeriodReader periods, UserRepository users,
            SaturdayModeResolver saturdays, LeaveBalanceService balances, Clock clock) {
        this.punches = punches;
        this.days = days;
        this.holidays = holidays;
        this.requests = requests;
        this.context = context;
        this.periods = periods;
        this.users = users;
        this.saturdays = saturdays;
        this.balances = balances;
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
        recompute(tenantId, userId, date, evenIfLocked, () -> periods.read(tenantId, userId, date, date));
    }

    /** The punch path: the day's periods were read before its transaction, so no second connection is taken inside it. */
    @Transactional
    public void recompute(String tenantId, String userId, LocalDate date, Optional<List<TeacherPeriod>> dayPeriods) {
        recompute(tenantId, userId, date, false, () -> dayPeriods);
    }

    /** {@link #recomputeDays(String, String, List, Optional)} reading the range's periods here, once, in one query. */
    @Transactional
    public void recomputeDays(String tenantId, String userId, List<LocalDate> dates) {
        if (dates.isEmpty()) return;
        recomputeDays(tenantId, userId, dates, periods.read(tenantId, userId, dates.get(0), dates.get(dates.size() - 1)));
    }

    /**
     * Rebuilds the given days of one person (an approved request): every day goes through {@code recompute}, except a
     * day the person's school has no shift for and without a punch. That day is OFF or a holiday whatever the request
     * says, so it is left alone. {@code rangePeriods} is the calendar read of the whole range, taken once (and before
     * the caller's transaction when it can) instead of one connection borrow per day.
     */
    @Transactional
    public void recomputeDays(String tenantId, String userId, List<LocalDate> dates,
            Optional<List<TeacherPeriod>> rangePeriods) {
        if (dates.isEmpty()) return;
        List<TkShift> shifts = context.schoolOf(tenantId, userId).map(school -> context.shiftsOf(tenantId, school))
                .orElse(List.of());
        Set<LocalDate> punched = punches.findByTenantIdAndUserIdAndWorkDateBetweenOrderByServerTimeAscCodeAsc(tenantId,
                userId, dates.get(0), dates.get(dates.size() - 1)).stream().map(TkPunch::getWorkDate)
                .collect(Collectors.toSet());
        for (LocalDate date : dates) {
            if (!punched.contains(date) && WorkContextResolver.shiftOn(shifts, date).isEmpty()) continue;
            recompute(tenantId, userId, date, false, () -> rangePeriods.map(list -> TeacherPeriodReader.onDay(list, date)));
        }
    }

    /**
     * Rebuilds one day for many people after a day off or a Saturday mode changed (the same result as
     * {@link #recompute(String, String, LocalDate)} for each), with the reads shared: punches, approved requests,
     * stored rows and periods once for the group, holidays and shift once per school. A locked day is skipped.
     * shortcut: no per-person {@code lockDay} (a lock row per person would swamp the lock table); a punch recomputing the
     * same person in the same instants could be overwritten, so call this only from admin writes of a day off.
     */
    @Transactional
    public void recomputeMany(String tenantId, LocalDate date, Collection<String> userIds) {
        if (userIds.isEmpty()) return;
        Map<String, List<TkPunch>> punchesOf = punches
                .findByTenantIdAndUserIdInAndWorkDateBetweenOrderByServerTimeAscCodeAsc(tenantId, userIds, date, date).stream()
                .collect(Collectors.groupingBy(TkPunch::getUserId));
        Map<String, String> schoolOf = context.schoolsOf(tenantId, userIds);
        Map<String, List<TkRequest>> approvedOf = requests.findApprovedBetween(tenantId, userIds, date, date).stream()
                .collect(Collectors.groupingBy(TkRequest::getUserId));
        Map<String, TkDay> existingOf = days.findByTenantIdAndUserIdInAndWorkDateBetween(tenantId, userIds, date, date).stream()
                .collect(Collectors.toMap(TkDay::getUserId, Function.identity(), (a, b) -> a));
        List<TkHoliday> dayHolidays = holidays.findByTenantIdAndHolidayDate(tenantId, date);
        Optional<SaturdayMode> saturdayMode = saturdays.modeOn(tenantId, date);
        Optional<Map<String, List<TeacherPeriod>>> dayPeriods = periods.readMany(tenantId, userIds, date, date);
        Map<String, SchoolDay> schools = new HashMap<>();
        List<TkDay> rows = new ArrayList<>();
        List<TkDay> gone = new ArrayList<>();
        for (String userId : userIds) {
            List<TkPunch> dayPunches = punchesOf.getOrDefault(userId, List.of());
            String schoolId = schoolOf(dayPunches).orElse(schoolOf.get(userId));
            if (schoolId == null) continue;
            SchoolDay school = schools.computeIfAbsent(schoolId, id -> schoolDay(tenantId, id, date,
                    dayHolidays.stream().filter(h -> h.getSchoolId() == null || h.getSchoolId().equals(id)).toList(),
                    saturdayMode));
            if (school.locked()) continue;
            TkDay existing = existingOf.get(userId);
            Optional<TkDay> row = build(school, userId, date, dayPunches, approvedOf.getOrDefault(userId, List.of()),
                    existing, closed(school, date), () -> dayPeriods.map(all -> all.getOrDefault(userId, List.of())));
            if (row.isPresent()) {
                rows.add(row.get());
            } else if (existing != null) {
                gone.add(existing);
            }
        }
        days.saveAll(rows);
        days.deleteAll(gone);
    }

    private void recompute(String tenantId, String userId, LocalDate date, boolean evenIfLocked,
            Supplier<Optional<List<TeacherPeriod>>> dayPeriods) {
        lockDay(tenantId, userId, date);
        List<TkPunch> dayPunches = punches.findByTenantIdAndUserIdAndWorkDateOrderByServerTimeAscCodeAsc(tenantId, userId, date);
        String schoolId = schoolOf(dayPunches).or(() -> context.schoolOf(tenantId, userId)).orElse(null);
        if (schoolId == null) return;
        SchoolDay school = schoolDay(tenantId, schoolId, date, holidays.findForSchool(tenantId, schoolId, date, date),
                saturdays.modeOn(tenantId, date));
        if (school.locked() && !evenIfLocked) {
            log.info("Skipping recompute of a locked day: tenant {} user {} date {}", tenantId, userId, date);
            return;
        }
        Optional<TkDay> existing = days.findByTenantIdAndUserIdAndWorkDate(tenantId, userId, date);
        Optional<TkDay> row = build(school, userId, date, dayPunches,
                requests.findApprovedOnForUser(tenantId, userId, date), existing.orElse(null), closed(school, date), dayPeriods);
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
        Optional<SaturdayMode> saturdayMode = saturdays.modeOn(tenantId, date);
        Optional<Map<String, List<TeacherPeriod>>> dayPeriods = periods.readMany(tenantId, people, date, date);
        Map<String, SchoolDay> schools = new HashMap<>();

        List<TkDay> rows = new ArrayList<>();
        for (String userId : people) {
            try {
                List<TkPunch> dayPunches = punchesByUser.getOrDefault(userId, List.of());
                String schoolId = schoolOf(dayPunches).orElse(schoolOf.get(userId));
                if (schoolId == null) continue;
                SchoolDay school = schools.computeIfAbsent(schoolId, id -> schoolDay(tenantId, id, date,
                        dayHolidays.stream().filter(h -> h.getSchoolId() == null || h.getSchoolId().equals(id)).toList(),
                        saturdayMode));
                if (school.locked()) continue;
                build(school, userId, date, dayPunches, requestsByUser.getOrDefault(userId, List.of()),
                        existing.get(userId), true, () -> dayPeriods.map(all -> all.getOrDefault(userId, List.of())))
                        .ifPresent(rows::add);
            } catch (RuntimeException ex) {
                log.warn("Daily close failed for tenant {} user {} date {}: {}", tenantId, userId, date,
                        ex.getClass().getSimpleName());
            }
        }
        days.saveAll(rows);
        return rows.size();
    }

    /**
     * Closes every day of {@code month} for one school, before its period lock (I1): the nightly close may not have
     * reached the last day yet, or may have missed a night. The caller locks only afterwards, so nothing is skipped.
     * Same result as {@link #closeDay} for each day, but read once for the month and scoped to the school's people:
     * punches, approved requests and stored rows in one query each, the teaching periods in one, written in one batch.
     * Only the roster (its active status depends on the join/leave date) is still asked per day.
     */
    public void closeMonth(String tenantId, String schoolId, YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        Map<LocalDate, Set<String>> rosterOn = new LinkedHashMap<>();
        Set<String> candidates = new LinkedHashSet<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            Set<String> roster = new LinkedHashSet<>(users.findActiveUserIdsHoldingPermission(tenantId, SELF_PERMISSION, date));
            rosterOn.put(date, roster);
            candidates.addAll(roster);
        }
        Set<String> atSchool = punches.findByTenantIdAndSchoolIdAndWorkDateBetweenOrderByServerTimeAscCodeAsc(tenantId,
                schoolId, first, last).stream().map(TkPunch::getUserId).collect(Collectors.toSet());
        candidates.addAll(atSchool);
        Map<String, String> schoolOf = context.schoolsOf(tenantId, candidates);
        // a person closes here when their school is this one, or when they punched here (a punch's school wins that day)
        Set<String> members = candidates.stream()
                .filter(u -> atSchool.contains(u) || schoolId.equals(schoolOf.get(u))).collect(Collectors.toCollection(LinkedHashSet::new));
        if (members.isEmpty()) return;
        Map<String, Map<LocalDate, List<TkPunch>>> punchesOf = new HashMap<>();
        punches.findByTenantIdAndUserIdInAndWorkDateBetweenOrderByServerTimeAscCodeAsc(tenantId, members, first, last)
                .forEach(p -> punchesOf.computeIfAbsent(p.getUserId(), k -> new HashMap<>())
                        .computeIfAbsent(p.getWorkDate(), k -> new ArrayList<>()).add(p));
        Map<String, List<TkRequest>> approvedOf = requests.findApprovedBetween(tenantId, members, first, last).stream()
                .collect(Collectors.groupingBy(TkRequest::getUserId));
        Map<String, Map<LocalDate, TkDay>> existingOf = new HashMap<>();
        days.findByTenantIdAndUserIdInAndWorkDateBetween(tenantId, members, first, last)
                .forEach(d -> existingOf.computeIfAbsent(d.getUserId(), k -> new HashMap<>()).putIfAbsent(d.getWorkDate(), d));
        List<TkHoliday> monthHolidays = holidays.findForSchool(tenantId, schoolId, first, last);
        Optional<Map<String, List<TeacherPeriod>>> monthPeriods = periods.readMany(tenantId, members, first, last);

        List<TkDay> rows = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            LocalDate day = date;
            SchoolDay school = schoolDay(tenantId, schoolId, day,
                    monthHolidays.stream().filter(h -> day.equals(h.getHolidayDate())).toList(), saturdays.modeOn(tenantId, day));
            if (school.locked()) continue;
            Set<String> people = new LinkedHashSet<>(rosterOn.get(day));
            members.stream().filter(u -> punchesOf.getOrDefault(u, Map.of()).containsKey(day)).forEach(people::add);
            for (String userId : members) {
                if (!people.contains(userId)) continue;
                try {
                    List<TkPunch> dayPunches = punchesOf.getOrDefault(userId, Map.of()).getOrDefault(day, List.of());
                    if (!schoolId.equals(schoolOf(dayPunches).orElse(schoolOf.get(userId)))) continue;
                    List<TkRequest> approved = approvedOf.getOrDefault(userId, List.of()).stream().filter(r -> covers(r, day)).toList();
                    build(school, userId, day, dayPunches, approved, existingOf.getOrDefault(userId, Map.of()).get(day), true,
                            () -> monthPeriods.map(all -> TeacherPeriodReader.onDay(all.getOrDefault(userId, List.of()), day)))
                            .ifPresent(rows::add);
                } catch (RuntimeException ex) {
                    log.warn("Month close failed for tenant {} user {} date {}: {}", tenantId, userId, day,
                            ex.getClass().getSimpleName());
                }
            }
        }
        days.saveAll(rows);
    }

    /** Whether an approved request shapes {@code day}: LEAVE / BUSINESS covering it, an EXPLANATION for it. */
    private static boolean covers(TkRequest r, LocalDate day) {
        return r.getType() == RequestType.EXPLANATION ? day.equals(r.getWorkDate())
                : !day.isBefore(r.getFromDate()) && !day.isAfter(r.getToDate());
    }

    /**
     * Serialises recomputes of one (user, day) until commit, so a stale snapshot never overwrites a newer one (M1).
     * shortcut: closeDay does not take it (one lock per person per day would exhaust the lock table for a month
     * close); take it there too, or upsert, if the 00:30 close is seen racing approvals.
     */
    private void lockDay(String tenantId, String userId, LocalDate date) {
        requests.lockSubmitsOf("tk-day:" + tenantId + ":" + userId + ":" + date);
    }

    /** {@code holidayPay}: how the school's day off is paid (null = not a holiday); {@code saturday}: the day's Saturday mode. */
    private record SchoolDay(String tenantId, String schoolId, HolidayPayMode holidayPay, SaturdayMode saturday,
            boolean locked, TkShift shift, TkPolicy policy) {}

    private SchoolDay schoolDay(String tenantId, String schoolId, LocalDate date, List<TkHoliday> schoolHolidays,
            Optional<SaturdayMode> saturday) {
        // several rows on one day (system-wide and an old school row): the most favourable pay mode wins
        HolidayPayMode pay = schoolHolidays.stream().map(TkHoliday::getPayMode).min(Comparator.naturalOrder()).orElse(null);
        return new SchoolDay(tenantId, schoolId, pay, saturday.orElse(null), context.isLocked(tenantId, schoolId, date),
                context.shiftFor(tenantId, schoolId, date).orElse(null), context.policyFor(tenantId, schoolId, date));
    }

    /** How the day off is paid for this person, or null on a working day. A holiday beats a Saturday mode. */
    private static HolidayPayMode dayOffPay(SchoolDay school, TkShift shift, String userId) {
        if (school.holidayPay() != null) return school.holidayPay();
        if (school.saturday() == null || !SaturdayModeResolver.appliesTo(shift, userId)) return null;
        return switch (school.saturday()) {
            case PAID_OFF -> HolidayPayMode.PAID;
            case UNPAID_OFF -> HolidayPayMode.UNPAID;
            default -> null; // OFFICE / WFH: a normal working day (WFH only changes where punches are allowed)
        };
    }

    /** A day off with nothing worked: L (1 công), P (annual leave, while the balance lasts) or KL. */
    private void setDayOff(TkDay day, HolidayPayMode pay, TkShift shift, String tenantId, String userId, LocalDate date) {
        if (shift == null) {
            set(day, DayStatus.HOLIDAY, BigDecimal.ZERO); // a holiday on a non-working day is not paid twice
        } else if (pay == HolidayPayMode.PAID) {
            set(day, DayStatus.HOLIDAY, shift.getWorkUnits());
        } else if (pay == HolidayPayMode.ANNUAL_LEAVE
                && balances.chargedAnnualHolidays(tenantId, userId, date.getYear()).contains(date)) {
            set(day, DayStatus.LEAVE, BigDecimal.ZERO);
            day.setLeaveKind(LeaveKind.ANNUAL);
        } else {
            set(day, DayStatus.UNPAID_OFF, BigDecimal.ZERO);
        }
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
            List<TkRequest> approved, TkDay existing, boolean closed,
            Supplier<Optional<List<TeacherPeriod>>> dayPeriods) {
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
        HolidayPayMode offPay = dayOffPay(school, shift, userId);
        if (offPay != null && (shift == null || in.isEmpty() && out.isEmpty())) {
            setDayOff(day, offPay, shift, school.tenantId(), userId, date);
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
        // A failed read keeps the row's previous count (0 for a new row); the day itself is still written.
        dayPeriods.get().ifPresent(list -> day.setPeriods(list.stream().mapToInt(p -> p.periodCount()).sum()));
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
