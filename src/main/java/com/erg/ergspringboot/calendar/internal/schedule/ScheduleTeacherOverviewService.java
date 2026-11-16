package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.SchoolShortName;
import com.erg.ergspringboot.calendar.TeacherShortName;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.AccessChangeResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.LeaveInfoResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.LoadSettingsResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TeacherDayResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TeacherPageResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TeacherQuickCounts;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TeacherRowResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TeacherSlotResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TravelGapResponse;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Leave;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.TeacherRef;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleWeekAnalyzer.Week;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly overview by teacher (KE_HOACH_LICH_DAY.md B8): load, days and period slots of every teacher in scope, with the
 * clash / travel / two-schools-in-a-session / overload flags the quick filters use. Assistant periods count like main ones.
 */
@Service
public class ScheduleTeacherOverviewService {

    private final ScheduleDataLoader loader;
    private final ScheduleWeekAnalyzer analyzer;
    private final ScheduleLoadSettingsService settings;
    private final TravelTimeService travel;
    private final TeachingScheduleAccessQuery access;
    private final UserDirectoryPort users;
    private final ObjectProvider<Clock> clock;

    public ScheduleTeacherOverviewService(ScheduleDataLoader loader, ScheduleWeekAnalyzer analyzer,
            ScheduleLoadSettingsService settings, TravelTimeService travel, TeachingScheduleAccessQuery access,
            UserDirectoryPort users, ObjectProvider<Clock> clock) {
        this.loader = loader;
        this.analyzer = analyzer;
        this.settings = settings;
        this.travel = travel;
        this.access = access;
        this.users = users;
        this.clock = clock;
    }

    /** One teacher's events of the week with their role in each (parallel lists). */
    private record Mine(List<Occurrence> events, List<String> roles) {}

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public TeacherPageResponse teachers(String tenantId, Collection<String> scope, LocalDate weekStart, String search, String quick,
            String sort, int page, int size) {
        OffsetDateTime now = OffsetDateTime.now(clock.getIfAvailable(Clock::systemUTC));
        Week week = analyzer.analyze(tenantId, weekStart, scope);
        LoadSettingsResponse limits = settings.get(tenantId);

        // everyone in scope, plus anyone who teaches there this week without being listed under it
        Map<String, String> names = new LinkedHashMap<>();
        (scope == null ? users.findCalendarTeachersByTenant(tenantId) : users.findCalendarTeachersByTenantAndSchoolIds(tenantId, scope))
                .forEach(u -> names.put(u.id(), u.displayName()));
        Map<String, Mine> mine = new HashMap<>();
        for (Occurrence e : week.events()) {
            for (TeacherRef t : e.teachers()) {
                names.putIfAbsent(t.id(), t.name() == null ? t.id() : t.name());
                Mine m = mine.computeIfAbsent(t.id(), k -> new Mine(new ArrayList<>(), new ArrayList<>()));
                m.events().add(e);
                m.roles().add(t.role());
            }
        }
        Map<String, Integer> weekly = new HashMap<>();
        mine.forEach((id, m) -> weekly.put(id, m.events().size()));
        Map<String, List<Leave>> leaves = loader.loadLeaves(tenantId, names.keySet(), week.monday(), week.monday().plusDays(6));
        Map<String, AccessChangeResponse> notices = access.revokeNotices(tenantId, shortNames(names), weekly, now);
        Flags flags = flags(week);

        List<TeacherRowResponse> rows = new ArrayList<>();
        for (Map.Entry<String, String> teacher : names.entrySet()) {
            rows.add(row(tenantId, week, teacher.getKey(), teacher.getValue(), mine.getOrDefault(teacher.getKey(), new Mine(List.of(), List.of())),
                    leaves.getOrDefault(teacher.getKey(), List.of()), limits, notices.get(teacher.getKey()), flags));
        }
        TeacherQuickCounts counts = new TeacherQuickCounts(count(rows, "CONFLICT", limits), count(rows, "TRAVEL", limits),
                count(rows, "MULTI_SCHOOL", limits), count(rows, "OVERLOAD", limits), count(rows, "EMPTY", limits));

        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        boolean ascending = "LOAD_ASC".equals(sort);
        if (sort != null && !sort.isBlank() && !ascending && !"LOAD_DESC".equals(sort)) {
            throw TeachingScheduleException.badRequest("INVALID_SORT", "Cách sắp xếp không hợp lệ: " + sort);
        }
        Comparator<TeacherRowResponse> byLoad = Comparator.comparingInt(TeacherRowResponse::weeklyPeriods);
        List<TeacherRowResponse> filtered = rows.stream()
                .filter(r -> needle.isEmpty() || r.teacherName().toLowerCase(Locale.ROOT).contains(needle)
                        || names.get(r.teacherId()).toLowerCase(Locale.ROOT).contains(needle))
                .filter(r -> quick == null || quick.isBlank() || matches(r, quick, limits))
                .sorted((ascending ? byLoad : byLoad.reversed()).thenComparing(TeacherRowResponse::teacherName)).toList();
        int pageSize = Math.max(1, Math.min(size, 200));
        int from = Math.min(filtered.size(), Math.max(0, page) * pageSize);
        return new TeacherPageResponse(filtered.size(), filtered.subList(from, Math.min(filtered.size(), from + pageSize)), counts);
    }

    private static Map<String, String> shortNames(Map<String, String> names) {
        Map<String, String> result = new HashMap<>();
        names.forEach((id, name) -> result.put(id, TeacherShortName.of(name)));
        return result;
    }

    /**
     * Events in a clash of class or room (shared by everyone teaching them), per teacher the events they are double
     * booked in, and per teacher the events that start too soon after a period at another school.
     */
    private record Flags(Set<String> sharedClash, Map<String, Set<String>> teacherClash, Map<String, Set<String>> travelEvents) {
        boolean clashes(String teacherId, String eventId) {
            return sharedClash.contains(eventId) || teacherClash.getOrDefault(teacherId, Set.of()).contains(eventId);
        }
    }

    private static Flags flags(Week week) {
        Set<String> shared = new HashSet<>();
        Map<String, Set<String>> teacher = new HashMap<>();
        Map<String, Set<String>> travel = new HashMap<>();
        for (ConflictDetailResponse c : week.details()) {
            if (!"ERROR".equals(c.severity())) continue;
            if ("TRAVEL_TIME".equals(c.kind())) {
                travel.computeIfAbsent(c.mine().teacherId(), k -> new HashSet<>()).add(c.mine().eventId());
            } else if ("TEACHER_TIME".equals(c.kind())) {
                Set<String> events = teacher.computeIfAbsent(c.mine().teacherId(), k -> new HashSet<>());
                events.add(c.mine().eventId());
                events.add(c.other().eventId());
            } else {
                shared.add(c.mine().eventId());
                if (c.other() != null) shared.add(c.other().eventId());
            }
        }
        return new Flags(shared, teacher, travel);
    }

    private TeacherRowResponse row(String tenantId, Week week, String teacherId, String fullName, Mine mine, List<Leave> leaves,
            LoadSettingsResponse limits, AccessChangeResponse notice, Flags flags) {
        Set<String> myEvents = new HashSet<>();
        mine.events().forEach(e -> myEvents.add(e.eventId()));
        int clashes = (int) week.pairs().stream().filter(c -> "ERROR".equals(c.severity()) && !"TRAVEL_TIME".equals(c.kind()))
                .filter(c -> "TEACHER_TIME".equals(c.kind()) ? teacherId.equals(c.mine().teacherId())
                        : myEvents.contains(c.mine().eventId()) || (c.other() != null && myEvents.contains(c.other().eventId()))).count();
        Set<String> travelEvents = flags.travelEvents().getOrDefault(teacherId, Set.of());
        Leave weekLeave = leaves.isEmpty() ? null : leaves.get(0);

        List<TeacherDayResponse> days = new ArrayList<>();
        for (int weekday = 1; weekday <= 6; weekday++) {
            days.add(day(tenantId, week, weekday, mine, leaves));
        }
        // periods 5 and 10 (only when a school gave them a time) share the slot of the period before them
        Map<String, List<Integer>> slotRows = new TreeMap<>();
        for (int i = 0; i < mine.events().size(); i++) {
            Occurrence e = mine.events().get(i);
            if (e.weekday() > 6) continue;
            int slotPeriod = e.periodNo() == 5 ? 4 : e.periodNo() == 10 ? 9 : e.periodNo();
            slotRows.computeIfAbsent(String.format("%d-%02d", e.weekday(), slotPeriod), k -> new ArrayList<>()).add(i);
        }
        List<TeacherSlotResponse> slots = new ArrayList<>();
        slotRows.forEach((key, indexes) -> {
            List<Occurrence> events = indexes.stream().map(mine.events()::get).toList();
            Occurrence first = events.get(0);
            slots.add(new TeacherSlotResponse(first.weekday(), Integer.parseInt(key.substring(2)),
                    events.stream().map(Occurrence::eventId).toList(),
                    events.stream().map(e -> ScheduleConflictEngine.nz(e.className())).toList(),
                    events.stream().map(e -> SchoolShortName.of(e.schoolName())).toList(),
                    events.stream().anyMatch(e -> flags.clashes(teacherId, e.eventId())),
                    events.stream().anyMatch(e -> travelEvents.contains(e.eventId())),
                    events.stream().anyMatch(e -> leaves.stream().anyMatch(l -> l.covers(e))),
                    events.stream().anyMatch(e -> "DRAFT".equals(e.batchStatus())) ? "DRAFT" : "PUBLISHED",
                    indexes.stream().map(mine.roles()::get).toList()));
        });
        int schoolCount = (int) mine.events().stream().map(Occurrence::schoolId).distinct().count();
        return new TeacherRowResponse(teacherId, TeacherShortName.of(fullName), TeacherShortName.initials(fullName), schoolCount, clashes,
                mine.events().size(), limits.loadMax(),
                weekLeave == null ? null : new LeaveInfoResponse(weekLeave.from().toString(), weekLeave.to().toString(), weekLeave.part()),
                notice, days, slots);
    }

    private TeacherDayResponse day(String tenantId, Week week, int weekday, Mine mine, List<Leave> leaves) {
        List<Occurrence> events = mine.events().stream().filter(e -> e.weekday() == weekday).sorted(Comparator.comparing(Occurrence::start)).toList();
        List<String> schools = events.stream().map(e -> SchoolShortName.of(e.schoolName())).distinct().toList();
        boolean multi = false;
        for (boolean morning : new boolean[] {true, false}) {
            multi |= events.stream().filter(e -> (e.periodNo() <= 5) == morning).map(Occurrence::schoolId).distinct().count() >= 2;
        }
        // the closest pair of consecutive periods at two different schools, with the minutes the pair needs
        TravelGapResponse tightest = null;
        long tightestGap = Long.MAX_VALUE;
        for (int i = 1; i < events.size(); i++) {
            Occurrence earlier = events.get(i - 1);
            Occurrence later = events.get(i);
            if (earlier.schoolId().equals(later.schoolId())) continue;
            long gap = Duration.between(earlier.end(), later.start()).toMinutes();
            // overlapping periods are a clash, not a travel problem
            if (gap >= 0 && gap < tightestGap) {
                tightestGap = gap;
                tightest = ScheduleConflictEngine.travelGap(earlier, later, (int) gap, travel.requiredMinutes(tenantId, earlier.schoolId(), later.schoolId()));
            }
        }
        LocalDate date = week.monday().plusDays(weekday - 1L);
        return new TeacherDayResponse(weekday, schools, multi, tightest,
                leaves.stream().anyMatch(l -> !date.isBefore(l.from()) && !date.isAfter(l.to())));
    }

    private static boolean matches(TeacherRowResponse r, String quick, LoadSettingsResponse limits) {
        return switch (quick) {
            case "CONFLICT" -> r.conflictCount() > 0;
            case "TRAVEL" -> r.days().stream().anyMatch(d -> d.travel() != null && d.travel().gapMinutes() < d.travel().requiredMinutes());
            case "MULTI_SCHOOL" -> r.days().stream().anyMatch(TeacherDayResponse::multiSchoolSameSession);
            case "OVERLOAD" -> r.weeklyPeriods() > limits.loadWarn();
            case "EMPTY" -> r.weeklyPeriods() == 0;
            default -> throw TeachingScheduleException.badRequest("INVALID_FILTER", "Bộ lọc nhanh không hợp lệ: " + quick);
        };
    }

    private static int count(List<TeacherRowResponse> rows, String quick, LoadSettingsResponse limits) {
        return (int) rows.stream().filter(r -> matches(r, quick, limits)).count();
    }
}
