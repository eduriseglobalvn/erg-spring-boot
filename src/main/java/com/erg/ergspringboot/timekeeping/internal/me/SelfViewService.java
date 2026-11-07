package com.erg.ergspringboot.timekeeping.internal.me;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.Missing;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.RequestStatus;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.DayDetailView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.DayPunchView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.MonthDayView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.MonthView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PeriodView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.ShiftView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.SiteView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.TodayView;
import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.internal.request.LeaveBalanceService;
import com.erg.ergspringboot.timekeeping.internal.evidence.FaceConsentService;
import com.erg.ergspringboot.timekeeping.internal.evidence.PunchPhotoService;
import com.erg.ergspringboot.timekeeping.internal.punch.PunchService;
import com.erg.ergspringboot.timekeeping.internal.rules.ExplanationSuggester;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkDay;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkHoliday;
import com.erg.ergspringboot.timekeeping.persistence.TkHolidayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkPunch;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.timekeeping.persistence.TkSite;
import com.erg.ergspringboot.timekeeping.persistence.TkSiteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of "my timekeeping": today, the month and one day. Every query is scoped to the caller. */
@Service
@Transactional(readOnly = true)
public class SelfViewService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_ACCURACY_M = 100;

    private final WorkContextResolver context;
    private final TkPunchRepository punches;
    private final TkDayRepository days;
    private final TkSiteRepository sites;
    private final TkHolidayRepository holidays;
    private final TkRequestRepository requests;
    private final FaceConsentService consents;
    private final PunchPhotoService photos;
    private final CalendarReadPort calendar;
    private final LeaveBalanceService leaveBalance;
    private final Clock clock;

    SelfViewService(WorkContextResolver context, TkPunchRepository punches, TkDayRepository days, TkSiteRepository sites,
            TkHolidayRepository holidays, TkRequestRepository requests, FaceConsentService consents,
            PunchPhotoService photos, CalendarReadPort calendar, LeaveBalanceService leaveBalance, Clock clock) {
        this.context = context;
        this.punches = punches;
        this.days = days;
        this.sites = sites;
        this.holidays = holidays;
        this.requests = requests;
        this.consents = consents;
        this.photos = photos;
        this.calendar = calendar;
        this.leaveBalance = leaveBalance;
        this.clock = clock;
    }

    public TodayView today(String tenantId, String userId) {
        Instant now = clock.instant();
        LocalDate date = LocalDate.ofInstant(now, WorkContextResolver.ZONE);
        List<PeriodView> periods = periods(tenantId, userId, date, date);
        boolean consented = consents.hasConsent(tenantId, userId);
        Optional<String> school = context.schoolOf(tenantId, userId);
        if (school.isEmpty()) {
            return new TodayView(date, now, null, List.of(), true, false, consented, false, MAX_ACCURACY_M,
                    null, null, periods, null, false);
        }
        String schoolId = school.get();
        TkShift shift = context.shiftFor(tenantId, schoolId, date).orElse(null);
        TkPolicy policy = context.policyFor(tenantId, schoolId, date);
        List<TkPunch> today = punches.findByTenantIdAndUserIdAndWorkDateOrderByServerTimeAscCodeAsc(tenantId, userId, date);
        Map<String, String> siteNames = siteNames(tenantId, schoolId);
        return new TodayView(date, now, shift(shift),
                context.activeSites(tenantId, schoolId).stream().map(SelfViewService::site).toList(),
                policy.getRequireFace(), policy.getAllowOutside(), consented,
                shift != null && context.isRemote(tenantId, schoolId, userId, date, shift.getId()), MAX_ACCURACY_M,
                DayAggregationService.firstIn(today).map(p -> PunchService.view(p, siteNames.get(p.getSiteId())))
                        .orElse(null),
                DayAggregationService.latestOut(today).map(p -> PunchService.view(p, siteNames.get(p.getSiteId())))
                        .orElse(null),
                periods, shift == null ? null : shift.getEndTime(), context.isLocked(tenantId, schoolId, date));
    }

    public MonthView month(String tenantId, String userId, YearMonth period) {
        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();
        List<TkDay> dayRows = days.findByTenantIdAndUserIdAndWorkDateBetweenOrderByWorkDateAsc(tenantId, userId, from, to);
        Map<LocalDate, List<TkPunch>> punchesByDay = punches
                .findByTenantIdAndUserIdAndWorkDateBetweenOrderByServerTimeAscCodeAsc(tenantId, userId, from, to).stream()
                .collect(Collectors.groupingBy(TkPunch::getWorkDate));
        List<MonthDayView> dayViews = dayRows.stream().map(d -> new MonthDayView(d.getWorkDate(), d.getStatus(),
                d.getWorkUnits(), flags(d.getRemote(), punchesByDay.getOrDefault(d.getWorkDate(), List.of())))).toList();
        BigDecimal actual = dayRows.stream().map(TkDay::getWorkUnits).reduce(BigDecimal.ZERO, BigDecimal::add);
        int lateCount = (int) dayRows.stream().filter(d -> d.getLateMinutes() > 0).count();
        int lateMinutes = dayRows.stream().mapToInt(TkDay::getLateMinutes).sum();

        Optional<String> school = context.schoolOf(tenantId, userId);
        TkPolicy policy = context.policyFor(tenantId, school.orElse(""), from);
        BigDecimal standard = school.map(s -> standardUnits(tenantId, s, from, to)).orElse(BigDecimal.ZERO);
        BigDecimal leaveRemaining = leaveBalance.annualBalance(tenantId, userId, period.getYear());
        int lockDay = Math.min(policy.getLockDayOfMonth(), period.plusMonths(1).lengthOfMonth());
        return new MonthView(period.toString(), actual.setScale(1), standard.setScale(1), lateCount, lateMinutes,
                leaveRemaining, requests.countByTenantIdAndUserIdAndStatus(tenantId, userId, RequestStatus.PENDING),
                periods(tenantId, userId, from, to).size(), dayViews, period.plusMonths(1).atDay(lockDay),
                school.map(s -> context.isLocked(tenantId, s, from)).orElse(false));
    }

    public DayDetailView day(ErgPrincipal principal, LocalDate date) {
        String tenantId = principal.tenantId();
        String userId = principal.userId();
        List<TkPunch> dayPunches = punches.findByTenantIdAndUserIdAndWorkDateOrderByServerTimeAscCodeAsc(tenantId, userId, date);
        Optional<TkDay> day = days.findByTenantIdAndUserIdAndWorkDate(tenantId, userId, date);
        Optional<String> school = context.schoolOf(tenantId, userId);
        TkShift shift = school.flatMap(s -> context.shiftFor(tenantId, s, date)).orElse(null);
        TkPolicy policy = context.policyFor(tenantId, school.orElse(""), date);
        Map<String, String> siteNames = school.map(s -> siteNames(tenantId, s)).orElse(Map.of());
        Function<TkPunch, DayPunchView> withPhoto = p -> new DayPunchView(
                PunchService.view(p, siteNames.get(p.getSiteId())),
                p.getPhotoKey() == null ? null : photos.photoUrl(principal, p.getId()).map(Object::toString).orElse(null),
                p.getPhotoPurgedAt() != null);
        List<PeriodView> periods = periods(tenantId, userId, date, date);
        Missing missing = day.map(TkDay::getMissing).orElse(null);
        YearMonth month = YearMonth.from(date);
        long usedExplanations = requests.countActiveExplanations(tenantId, userId, month.atDay(1), month.atEndOfMonth());
        Optional<LocalTime> lastPeriodEnd = periods.stream().reduce((a, b) -> b)
                .map(p -> LocalTime.ofInstant(p.end(), WorkContextResolver.ZONE));
        return new DayDetailView(date, day.map(TkDay::getStatus).orElse(null), shift(shift),
                DayAggregationService.firstIn(dayPunches).map(withPhoto).orElse(null),
                DayAggregationService.latestOut(dayPunches).map(withPhoto).orElse(null),
                dayPunches.stream().map(withPhoto).toList(),
                missing, missingReason(missing, shift), periods,
                day.map(TkDay::getWorkUnits).orElse(null),
                day.map(TkDay::getLateMinutes).orElse(0), day.map(TkDay::getEarlyMinutes).orElse(0),
                day.map(TkDay::getRemote).orElse(false),
                shift == null ? null : ExplanationSuggester.suggestOut(shift.getEndTime(), lastPeriodEnd),
                (int) Math.max(0, policy.getExplanationsPerMonth() - usedExplanations),
                flags(day.map(TkDay::getRemote).orElse(false), dayPunches));
    }

    /** Working days of the month (shift weekdays minus holidays) times the shift's units. */
    private BigDecimal standardUnits(String tenantId, String schoolId, LocalDate from, LocalDate to) {
        Set<LocalDate> off = holidays.findForSchool(tenantId, schoolId, from, to).stream()
                .map(TkHoliday::getHolidayDate).collect(Collectors.toSet());
        List<TkShift> schoolShifts = context.shiftsOf(tenantId, schoolId);
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (off.contains(d)) continue;
            Optional<TkShift> shift = WorkContextResolver.shiftOn(schoolShifts, d);
            if (shift.isPresent()) total = total.add(shift.get().getWorkUnits());
        }
        return total;
    }

    private List<PeriodView> periods(String tenantId, String userId, LocalDate from, LocalDate to) {
        List<TeacherPeriod> found = calendar.teacherPeriods(tenantId, userId, from, to);
        return found.stream().map(p -> new PeriodView(p.start(), p.end(), p.className(), p.subject(), p.room())).toList();
    }

    private Map<String, String> siteNames(String tenantId, String schoolId) {
        return sites.findByTenantIdAndSchoolIdAndActiveTrueOrderByNameAsc(tenantId, schoolId).stream()
                .collect(Collectors.toMap(TkSite::getId, TkSite::getName));
    }

    private static List<String> flags(Boolean remote, List<TkPunch> dayPunches) {
        List<String> flags = new ArrayList<>();
        if (Boolean.TRUE.equals(remote)) flags.add("REMOTE");
        for (PunchStatus status : List.of(PunchStatus.FLAGGED, PunchStatus.PENDING_REVIEW)) {
            if (dayPunches.stream().anyMatch(p -> p.getStatus() == status)) flags.add(status.name());
        }
        return flags;
    }

    private static String missingReason(Missing missing, TkShift shift) {
        if (missing == null) return null;
        if (missing == Missing.IN) return "Không có lượt chấm vào";
        if (shift == null) return "Không có lượt chấm ra";
        return "Không có lượt chấm ra trong " + shift.getEndTime().format(HH_MM) + "–"
                + shift.getEndTime().plusMinutes(60).format(HH_MM);
    }

    private static ShiftView shift(TkShift s) {
        return s == null ? null : new ShiftView(s.getId(), s.getName(), s.getStartTime(), s.getEndTime(), s.getWorkUnits());
    }

    private static SiteView site(TkSite s) {
        return new SiteView(s.getId(), s.getName(), s.getLat().doubleValue(), s.getLng().doubleValue(), s.getRadiusM());
    }
}
