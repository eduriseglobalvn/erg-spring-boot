package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.timekeeping.DayStatus;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.PeriodStage;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.DayCell;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.Timesheet;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.TimesheetIssues;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.TimesheetRow;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkDay;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkHoliday;
import com.erg.ergspringboot.timekeeping.persistence.TkHolidayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.users.persistence.EmployeeProfile;
import com.erg.ergspringboot.users.persistence.EmployeeProfileRepository;
import com.erg.ergspringboot.users.persistence.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A02 month sheet of one school. Roster = active timekeeping.self holders of the school plus anyone with a day
 * row in the month; one day-row query for the page. Symbols (plan "Ký hiệu bảng công"): X FULL, M LATE_EARLY,
 * ½ HALF, P paid leave (ANNUAL / PAID_PERSONAL), Ô SICK, CT BUSINESS, KC MISSING_PUNCH / ABSENT_UNPAID, KP UNPAID
 * leave, L HOLIDAY, "" OFF. Holidays carry 0 công and are not in {@code standardDays}, so a holiday on a
 * non-working weekday is never paid twice.
 */
@Service
public class TimesheetService {

    private static final int MAX_SIZE = 200;
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final UserRepository users;
    private final EmployeeProfileRepository profiles;
    private final WorkContextResolver context;
    private final TkDayRepository days;
    private final TkHolidayRepository holidays;
    private final TkRequestRepository requests;
    private final AdminSupport support;

    TimesheetService(UserRepository users, EmployeeProfileRepository profiles, WorkContextResolver context,
            TkDayRepository days, TkHolidayRepository holidays, TkRequestRepository requests, AdminSupport support) {
        this.users = users;
        this.profiles = profiles;
        this.context = context;
        this.days = days;
        this.holidays = holidays;
        this.requests = requests;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public Timesheet timesheet(String tenantId, String schoolId, String period, String team, String q, int page,
            int size) {
        YearMonth month = AdminSupport.period(period);
        support.requireSchool(tenantId, schoolId);
        int pageSize = Math.max(1, Math.min(size, MAX_SIZE));
        int pageNo = Math.max(0, page);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        LocalDate today = support.today();
        LocalDate rosterDate = today.isBefore(from) ? from : today.isAfter(to) ? to : today;

        Set<String> ids = new LinkedHashSet<>();
        context.schoolsOf(tenantId, users.findActiveUserIdsHoldingPermission(tenantId, AdminSupport.SELF, rosterDate))
                .forEach((user, school) -> {
                    if (schoolId.equals(school)) ids.add(user);
                });
        ids.addAll(days.findUserIdsWithRows(tenantId, schoolId, from, to));
        Map<String, String> names = support.names(tenantId, ids);
        Map<String, EmployeeProfile> profileOf = ids.isEmpty() ? Map.of()
                : profiles.findByTenantIdAndUserIdIn(tenantId, ids).stream()
                        .collect(Collectors.toMap(EmployeeProfile::getUserId, Function.identity(), (a, b) -> a));
        String needle = q == null || q.isBlank() ? null : fold(q.strip());
        List<String> people = ids.stream()
                .filter(u -> team == null || team.isBlank() || profileOf.containsKey(u)
                        && team.equals(profileOf.get(u).getDepartmentId()))
                .filter(u -> needle == null || fold(names.getOrDefault(u, "")).contains(needle)
                        || profileOf.containsKey(u) && fold(profileOf.get(u).getEmployeeCode()).contains(needle))
                .sorted(Comparator.comparing((String u) -> fold(names.getOrDefault(u, ""))).thenComparing(u -> u))
                .toList();
        List<String> pageIds = people.stream().skip((long) pageNo * pageSize).limit(pageSize).toList();
        Map<String, List<TkDay>> rowsOf = pageIds.isEmpty() ? Map.of()
                : days.findByTenantIdAndSchoolIdAndUserIdInAndWorkDateBetween(tenantId, schoolId, pageIds, from, to)
                        .stream().collect(Collectors.groupingBy(TkDay::getUserId));
        List<TimesheetRow> rows = pageIds.stream().map(u -> row(u, names.get(u), profileOf.get(u),
                rowsOf.getOrDefault(u, List.of()), month)).toList();

        PeriodStage stage = context.isLocked(tenantId, schoolId, from) ? PeriodStage.LOCKED
                : today.isAfter(to) ? PeriodStage.REVIEW : PeriodStage.COLLECT;
        LocalDate lockDay = month.plusMonths(1).atDay(1);
        LocalDate reviewUntil = lockDay.withDayOfMonth(context.policyFor(tenantId, schoolId, lockDay).getLockDayOfMonth());
        TimesheetIssues issues = new TimesheetIssues(
                days.countByTenantIdAndSchoolIdAndStatusAndWorkDateBetween(tenantId, schoolId, DayStatus.MISSING_PUNCH,
                        from, to),
                requests.countPendingTouching(tenantId, schoolId, from, to));
        return new Timesheet(schoolId, month.toString(), standardDays(tenantId, schoolId, month), stage, reviewUntil,
                issues, rows, pageNo, pageSize, people.size());
    }

    /** Working days of the month from the school's active shift weekdays, minus holidays. */
    int standardDays(String tenantId, String schoolId, YearMonth month) {
        List<TkShift> shifts = context.shiftsOf(tenantId, schoolId);
        Set<LocalDate> off = holidays.findForSchool(tenantId, schoolId, month.atDay(1), month.atEndOfMonth()).stream()
                .map(TkHoliday::getHolidayDate).collect(Collectors.toSet());
        return (int) month.atDay(1).datesUntil(month.atEndOfMonth().plusDays(1))
                .filter(d -> !off.contains(d) && WorkContextResolver.shiftOn(shifts, d).isPresent()).count();
    }

    static String code(TkDay day) {
        return switch (day.getStatus()) {
            case FULL -> "X";
            case LATE_EARLY -> "M";
            case HALF -> "½";
            case LEAVE -> day.getLeaveKind() == LeaveKind.UNPAID ? "KP" : "P";
            case SICK -> "Ô";
            case BUSINESS -> "CT";
            case MISSING_PUNCH, ABSENT_UNPAID -> "KC";
            case HOLIDAY -> "L";
            case OFF -> "";
        };
    }

    private static TimesheetRow row(String userId, String name, EmployeeProfile profile, List<TkDay> rows,
            YearMonth month) {
        Map<Integer, String> codes = rows.stream()
                .collect(Collectors.toMap(d -> d.getWorkDate().getDayOfMonth(), TimesheetService::code, (a, b) -> a));
        List<DayCell> cells = new ArrayList<>();
        for (int d = 1; d <= month.lengthOfMonth(); d++) cells.add(new DayCell(d, codes.getOrDefault(d, "")));
        BigDecimal work = BigDecimal.ZERO;
        BigDecimal leave = BigDecimal.ZERO;
        BigDecimal kpKc = BigDecimal.ZERO;
        int late = 0;
        int periods = 0;
        int deduct = 0;
        for (TkDay day : rows) {
            work = work.add(day.getWorkUnits());
            periods += day.getPeriods();
            deduct += day.getDeductMinutes();
            if (day.getLateMinutes() > 0) late++; // same rule as the today dashboard
            boolean unpaid = day.getLeaveKind() == LeaveKind.UNPAID;
            switch (day.getStatus()) {
                case LEAVE -> {
                    if (unpaid) kpKc = kpKc.add(BigDecimal.ONE);
                    else leave = leave.add(BigDecimal.ONE);
                }
                case SICK -> leave = leave.add(BigDecimal.ONE);
                case HALF -> {
                    if (unpaid) kpKc = kpKc.add(HALF);
                    else leave = leave.add(HALF);
                }
                case MISSING_PUNCH, ABSENT_UNPAID -> kpKc = kpKc.add(BigDecimal.ONE);
                default -> { }
            }
        }
        return new TimesheetRow(userId, name, profile == null ? null : profile.getEmployeeCode(),
                profile == null ? null : profile.getDepartmentId(), cells, work.setScale(1, RoundingMode.HALF_UP), late,
                leave.setScale(1, RoundingMode.HALF_UP), kpKc.setScale(1, RoundingMode.HALF_UP), periods, deduct);
    }

    /** Lower case without Vietnamese diacritics, so "nguyen" finds "Nguyễn". */
    static String fold(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd')
                .replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }
}
