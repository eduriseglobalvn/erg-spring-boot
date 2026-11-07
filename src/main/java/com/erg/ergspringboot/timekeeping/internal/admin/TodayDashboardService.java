package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.common.request.notifications.NotificationRequests.AdminSendRequest;
import com.erg.ergspringboot.notifications.NotificationAdminOperations;
import com.erg.ergspringboot.timekeeping.PunchDirection;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.ActionItem;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.Bucket;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.RecentPunch;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.RemindResult;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.SiteCount;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.TodayDashboard;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkDay;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPunch;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequest;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStep;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestStepRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkSite;
import com.erg.ergspringboot.timekeeping.persistence.TkSiteRepository;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * A01 "Chấm công hôm nay" for one school or every configured school of the tenant. Everything is read in bulk
 * (roster, schools, punches, day rows, approved requests, current approval steps, one calendar read for the
 * not-punched roster). Not transactional on purpose: a calendar failure only blanks the "period soon" count.
 */
@Service
public class TodayDashboardService {

    private static final Logger log = LoggerFactory.getLogger(TodayDashboardService.class);
    private static final Duration PERIOD_SOON = Duration.ofMinutes(30);
    private static final Duration OVERDUE_ACTION = Duration.ofHours(24);
    private static final int RECENT = 20;
    private static final int REMIND_MAX = 200;

    private final UserRepository users;
    private final WorkContextResolver context;
    private final TkPunchRepository punches;
    private final TkDayRepository days;
    private final TkRequestRepository requests;
    private final TkRequestStepRepository steps;
    private final TkShiftRepository shifts;
    private final TkSiteRepository sites;
    private final CalendarReadPort calendar;
    private final NotificationAdminOperations notifications;
    private final AdminSupport support;

    TodayDashboardService(UserRepository users, WorkContextResolver context, TkPunchRepository punches,
            TkDayRepository days, TkRequestRepository requests, TkRequestStepRepository steps, TkShiftRepository shifts,
            TkSiteRepository sites, CalendarReadPort calendar, NotificationAdminOperations notifications,
            AdminSupport support) {
        this.users = users;
        this.context = context;
        this.punches = punches;
        this.days = days;
        this.requests = requests;
        this.steps = steps;
        this.shifts = shifts;
        this.sites = sites;
        this.calendar = calendar;
        this.notifications = notifications;
        this.support = support;
    }

    /** {@code schoolId} null = every school with shifts or sites; {@code date} null = today in Vietnam. */
    public TodayDashboard today(String tenantId, String schoolId, LocalDate date) {
        LocalDate day = date == null ? support.today() : date;
        Set<String> schools = schoolId != null ? Set.of(support.requireSchool(tenantId, schoolId).getId())
                : configuredSchools(tenantId);
        Instant now = support.clock().instant();

        Map<String, String> schoolOf = context.schoolsOf(tenantId,
                users.findActiveUserIdsHoldingPermission(tenantId, AdminSupport.SELF, day));
        Set<String> roster = schoolOf.entrySet().stream().filter(e -> schools.contains(e.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedHashSet::new));
        List<TkPunch> dayPunches = punches.findByTenantIdAndWorkDateOrderByServerTimeAscCodeAsc(tenantId, day).stream()
                .filter(p -> schools.contains(p.getSchoolId())).toList();
        List<TkPunch> counted = dayPunches.stream().filter(p -> p.getStatus() != PunchStatus.REJECTED).toList();
        Map<String, String> presentAt = new TreeMap<>(); // user -> school of the first counted IN
        counted.stream().filter(p -> p.getDirection() == PunchDirection.IN)
                .forEach(p -> presentAt.putIfAbsent(p.getUserId(), p.getSchoolId()));
        List<TkDay> late = days.findByTenantIdAndWorkDate(tenantId, day).stream()
                .filter(d -> schools.contains(d.getSchoolId()) && d.getLateMinutes() > 0).toList();

        Map<RequestType, Set<String>> away = requests.findApprovedOn(tenantId, day).stream()
                .filter(r -> schools.contains(r.getSchoolId()) && r.getType() != RequestType.EXPLANATION)
                .collect(Collectors.groupingBy(TkRequest::getType, Collectors.mapping(TkRequest::getUserId,
                        Collectors.toSet())));
        Set<String> onLeave = away.getOrDefault(RequestType.LEAVE, Set.of());
        Set<String> onBusiness = away.getOrDefault(RequestType.BUSINESS, Set.of());
        // WFH people still punch (remotely), so only leave, business trips and a day without a shift excuse it
        Set<String> working = schoolsWithShift(tenantId, schools, day);
        Set<String> notPunched = roster.stream()
                .filter(u -> !presentAt.containsKey(u) && !onLeave.contains(u) && !onBusiness.contains(u)
                        && working.contains(schoolOf.get(u)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> periodSoon = periodSoon(tenantId, notPunched, day, now);

        List<TkRequestStep> waiting = steps.findCurrentWaitingSteps(tenantId, schools);
        Set<String> pending = waiting.stream().map(TkRequestStep::getRequestId).collect(Collectors.toSet());
        Set<String> overdue = waiting.stream().filter(s -> s.getDueAt() != null && s.getDueAt().isBefore(now))
                .map(TkRequestStep::getRequestId).collect(Collectors.toSet());
        Set<String> overdueAction = waiting.stream()
                .filter(s -> s.getDueAt() != null && s.getDueAt().isBefore(now.minus(OVERDUE_ACTION)))
                .map(TkRequestStep::getRequestId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<TkRequest> overdueRequests = overdueAction.isEmpty() ? List.of()
                : requests.findAllById(overdueAction).stream().filter(r -> tenantId.equals(r.getTenantId())).toList();
        List<TkPunch> toReview = dayPunches.stream()
                .filter(p -> p.getStatus() == PunchStatus.FLAGGED || p.getStatus() == PunchStatus.PENDING_REVIEW).toList();
        List<TkPunch> recent = dayPunches.stream().sorted(Comparator.comparing(TkPunch::getServerTime).reversed())
                .limit(RECENT).toList();

        Set<String> people = new HashSet<>(periodSoon);
        toReview.forEach(p -> people.add(p.getUserId()));
        recent.forEach(p -> people.add(p.getUserId()));
        overdueRequests.forEach(r -> people.add(r.getUserId()));
        Map<String, String> names = support.names(tenantId, people);
        Map<String, String> siteNames = sites.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(TkSite::getId, TkSite::getName, (a, b) -> a));

        List<ActionItem> actions = new ArrayList<>();
        periodSoon.forEach(u -> actions.add(new ActionItem("NOT_PUNCHED_PERIOD_SOON", u, u, names.get(u),
                "Chưa chấm công, có tiết trong 30 phút tới")));
        toReview.forEach(p -> actions.add(new ActionItem("PUNCH_REVIEW", p.getId(), p.getUserId(),
                names.get(p.getUserId()), p.getReason() == null ? "Lượt chấm cần duyệt" : p.getReason())));
        overdueRequests.forEach(r -> actions.add(new ActionItem("REQUEST_OVERDUE", r.getId(), r.getUserId(),
                names.get(r.getUserId()), "Đơn " + r.getCode() + " quá hạn duyệt hơn 24 giờ")));

        Map<String, String> schoolNames = support.schoolNames(tenantId, schools);
        Map<String, Long> lateBySchool = late.stream().collect(Collectors.groupingBy(TkDay::getSchoolId, Collectors.counting()));
        List<SiteCount> bySite = schools.stream().sorted().map(s -> new SiteCount(s, schoolNames.get(s),
                (int) roster.stream().filter(u -> s.equals(schoolOf.get(u))).count(),
                (int) presentAt.values().stream().filter(s::equals).count(),
                lateBySchool.getOrDefault(s, 0L).intValue())).toList();

        return new TodayDashboard(day, roster.size(), presentAt.size(), late.size(),
                late.stream().mapToInt(TkDay::getLateMinutes).sum(), notPunched.size(), periodSoon.size(),
                onLeave.size(), onBusiness.size(), pending.size(), overdue.size(), buckets(counted), actions,
                recent.stream().map(p -> recent(p, names.get(p.getUserId()), siteNames.get(p.getSiteId()))).toList(),
                bySite);
    }

    /** "Nhắc chấm công" to tenant users without a counted IN today; one outbox key per person and day. */
    public RemindResult remind(String tenantId, String actorId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty() || userIds.size() > REMIND_MAX) {
            throw AdminSupport.bad("INVALID_USERS", "Chọn từ 1 đến 200 nhân sự");
        }
        LocalDate day = support.today();
        Set<String> wanted = new LinkedHashSet<>(userIds);
        Set<String> known = users.findByTenantIdAndIdIn(tenantId, wanted).stream().map(User::getId)
                .collect(Collectors.toSet());
        Set<String> punchedIn = punches.findByTenantIdAndWorkDateOrderByServerTimeAscCodeAsc(tenantId, day).stream()
                .filter(p -> p.getDirection() == PunchDirection.IN && p.getStatus() != PunchStatus.REJECTED)
                .map(TkPunch::getUserId).collect(Collectors.toSet());
        Map<String, String> schoolOf = context.schoolsOf(tenantId, known);
        Set<String> working = schoolsWithShift(tenantId, new HashSet<>(schoolOf.values()), day);
        int sent = 0;
        for (String userId : wanted) {
            if (!known.contains(userId) || punchedIn.contains(userId) || !working.contains(schoolOf.get(userId))) {
                continue;
            }
            try {
                notifications.send(tenantId, actorId, new AdminSendRequest("tk-remind:" + userId + ":" + day,
                        "TIMEKEEPING_PUNCH_REMINDER", "timekeeping_day", userId, "Nhắc chấm công",
                        "Bạn chưa chấm công vào hôm nay. Nhớ chấm công.", "/teacher/timekeeping/punch", "normal", "lms",
                        List.of(userId), null, Map.of()));
                sent++;
            } catch (RuntimeException ex) {
                log.warn("Punch reminder failed for user {}: {}", userId, ex.getClass().getSimpleName());
            }
        }
        return new RemindResult(sent, wanted.size() - sent);
    }

    static RecentPunch recent(TkPunch p, String userName, String siteName) {
        return new RecentPunch(p.getId(), p.getCode(), p.getUserId(), userName, p.getSchoolId(), siteName,
                p.getServerTime(), p.getDirection(), p.getStatus(), Boolean.TRUE.equals(p.getRemote()),
                p.getPhotoKey() != null);
    }

    /** Schools of {@code schools} with a shift in effect on {@code day} (one shift query per school). */
    private Set<String> schoolsWithShift(String tenantId, Set<String> schools, LocalDate day) {
        return schools.stream().filter(s -> context.shiftFor(tenantId, s, day).isPresent()).collect(Collectors.toSet());
    }

    private Set<String> configuredSchools(String tenantId) {
        Set<String> schools = new HashSet<>();
        shifts.findByTenantId(tenantId).forEach(s -> schools.add(s.getSchoolId()));
        sites.findByTenantIdAndActiveTrue(tenantId).forEach(s -> schools.add(s.getSchoolId()));
        return schools;
    }

    /** Only for today: who of {@code notPunched} has a period starting in the next 30 minutes (one calendar read). */
    private Set<String> periodSoon(String tenantId, Set<String> notPunched, LocalDate day, Instant now) {
        if (notPunched.isEmpty() || !day.equals(support.today())) return Set.of();
        try {
            Set<String> soon = calendar.teachersWithPeriodStarting(tenantId, notPunched, now, now.plus(PERIOD_SOON));
            return notPunched.stream().filter(soon::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (RuntimeException ex) {
            log.warn("Period-soon read failed for tenant {}: {}", tenantId, ex.getClass().getSimpleName());
            return Set.of();
        }
    }

    /** 15-minute buckets (Vietnam time) holding at least one counted punch, in time order. */
    private static List<Bucket> buckets(List<TkPunch> counted) {
        Map<LocalTime, int[]> byStart = new TreeMap<>();
        for (TkPunch p : counted) {
            LocalTime t = p.getServerTime().atZone(WorkContextResolver.ZONE).toLocalTime();
            LocalTime start = LocalTime.of(t.getHour(), t.getMinute() / 15 * 15);
            int[] counts = byStart.computeIfAbsent(start, k -> new int[2]);
            counts[p.getDirection() == PunchDirection.IN ? 0 : 1]++;
        }
        return byStart.entrySet().stream().map(e -> new Bucket(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
    }
}
