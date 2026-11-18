package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.SchoolShortName;
import com.erg.ergspringboot.calendar.TeacherShortName;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.HolidayResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.OverviewCellResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.OverviewGridResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.PeriodSlotResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.PeriodTemplateResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolPageResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolQuickCounts;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolRowResponse;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodTemplate;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.SchoolRef;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleWeekAnalyzer.Week;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Weekly overview by school: the school list with counters, and the side-by-side grid of one page of schools. */
@Service
public class ScheduleOverviewService {

    /** One request = one page of schools; the client pages through any number of selected schools (BUG-B-021). */
    static final int MAX_GRID_SCHOOLS = 50;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final ScheduleDataLoader loader;
    private final ScheduleWeekAnalyzer analyzer;
    private final PeriodTemplateService templates;

    public ScheduleOverviewService(ScheduleDataLoader loader, ScheduleWeekAnalyzer analyzer, PeriodTemplateService templates) {
        this.loader = loader;
        this.analyzer = analyzer;
        this.templates = templates;
    }

    /** {@code scope} null = every school of the tenant. Conflicting schools first, then drafts, then name; pages start at 0. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public SchoolPageResponse schools(String tenantId, Collection<String> scope, LocalDate weekStart, String search, String quick,
            int page, int size) {
        List<SchoolRef> schools = loader.loadSchools(tenantId, scope);
        Week week = analyzer.analyze(tenantId, weekStart, scope);
        Counters counters = counters(week);
        List<SchoolRowResponse> rows = schools.stream().map(s -> row(s, week, counters)).toList();
        Set<String> travel = counters.travelSchools;
        SchoolQuickCounts quickCounts = new SchoolQuickCounts((int) rows.stream().filter(r -> r.conflictCount() > 0).count(),
                (int) rows.stream().filter(r -> travel.contains(r.schoolId())).count(), (int) rows.stream().filter(SchoolRowResponse::hasDraft).count(),
                (int) rows.stream().filter(r -> r.periodCount() == 0).count());

        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Map<String, String> haystack = new HashMap<>();
        if (!needle.isEmpty()) {
            week.events().forEach(e -> haystack.merge(e.schoolId(), (ScheduleConflictEngine.nz(e.className()) + " " + e.teachers().stream()
                    .map(t -> ScheduleConflictEngine.nz(t.name())).collect(Collectors.joining(" "))).toLowerCase(Locale.ROOT), (a, b) -> a + " " + b));
        }
        List<SchoolRowResponse> filtered = rows.stream()
                .filter(r -> needle.isEmpty() || r.schoolName().toLowerCase(Locale.ROOT).contains(needle)
                        || haystack.getOrDefault(r.schoolId(), "").contains(needle))
                .filter(r -> matchesQuick(r, quick, travel))
                .sorted(Comparator.comparing((SchoolRowResponse r) -> r.conflictCount() == 0).thenComparing(r -> !r.hasDraft())
                        .thenComparing(SchoolRowResponse::schoolName)).toList();
        int pageSize = Math.max(1, Math.min(size, 200));
        int from = Math.min(filtered.size(), Math.max(0, page) * pageSize);
        return new SchoolPageResponse(filtered.size(), filtered.subList(from, Math.min(filtered.size(), from + pageSize)), quickCounts);
    }

    /**
     * One page of schools side by side: every period of the week, the conflicts, and the holidays that week. Not read-only:
     * the first look-up of a tenant seeds its default period template.
     */
    @Transactional("postgresTransactionManager")
    public OverviewGridResponse grid(String tenantId, LocalDate weekStart, List<String> schoolIds) {
        List<String> ids = schoolIds == null ? List.of() : schoolIds.stream().distinct().toList();
        if (ids.size() > MAX_GRID_SCHOOLS) {
            throw TeachingScheduleException.badRequest("TOO_MANY_SCHOOLS", "Mỗi lần tải tối đa " + MAX_GRID_SCHOOLS + " trường.");
        }
        if (ids.isEmpty()) {
            throw TeachingScheduleException.badRequest("SCHOOL_REQUIRED", "Chọn ít nhất một trường.");
        }
        List<SchoolRef> schools = loader.loadSchools(tenantId, ids);
        if (schools.size() != ids.size()) {
            throw TeachingScheduleException.notFound("Trường không tồn tại trong phạm vi quản lý.");
        }
        Week week = analyzer.analyze(tenantId, weekStart, ids);
        Counters counters = counters(week);
        Map<String, String> notes = loader.names(tenantId, "teaching_schedule_events", "note",
                week.events().stream().map(Occurrence::eventId).toList());
        Map<String, Set<String>> kindsByEvent = new HashMap<>();
        for (ConflictDetailResponse c : week.details()) {
            kindsByEvent.computeIfAbsent(c.mine().eventId(), k -> new LinkedHashSet<>()).add(c.kind());
        }
        List<OverviewCellResponse> cells = new ArrayList<>();
        for (Occurrence e : week.events()) {
            String main = e.teachers().stream().filter(t -> "MAIN".equals(t.role())).map(t -> TeacherShortName.of(t.name())).findFirst().orElse(null);
            String assistant = e.teachers().stream().filter(t -> "ASSISTANT".equals(t.role())).map(t -> TeacherShortName.of(t.name())).findFirst().orElse(null);
            cells.add(new OverviewCellResponse(e.schoolId(), e.weekday(), e.periodNo(), e.eventId(), e.className(), e.roomName(), main,
                    assistant, e.batchStatus(), List.copyOf(kindsByEvent.getOrDefault(e.eventId(), Set.of())), notes.get(e.eventId()), e.batchLabel()));
        }
        cells.sort(Comparator.comparing(OverviewCellResponse::weekday).thenComparing(OverviewCellResponse::periodNo)
                .thenComparing(OverviewCellResponse::schoolId));
        List<HolidayResponse> holidays = loader.loadHolidays(tenantId, week.monday(), week.monday().plusDays(6), ids).entrySet().stream()
                .map(h -> new HolidayResponse(h.getKey().toString(), h.getValue().name(), h.getValue().kind())).toList();
        List<ConflictDetailResponse> conflicts = week.pairs().stream().filter(c -> ids.contains(c.mine().schoolId())).toList();
        return new OverviewGridResponse(week.monday().toString(), toResponse(templates.templateForSchool(tenantId, ids.get(0), week.monday())),
                schools.stream().sorted(Comparator.comparingInt(s -> ids.indexOf(s.id()))).map(s -> row(s, week, counters)).toList(),
                cells, holidays, conflicts);
    }

    /** Per-school counters of one week: conflicts count each clash once for each school it touches. */
    private record Counters(Map<String, Integer> conflicts, Set<String> travelSchools) {}

    private static Counters counters(Week week) {
        Map<String, Integer> conflicts = new HashMap<>();
        Set<String> travel = new HashSet<>();
        for (ConflictDetailResponse c : week.pairs()) {
            if (!"ERROR".equals(c.severity())) continue;
            Set<String> touched = new HashSet<>();
            touched.add(c.mine().schoolId());
            if (c.other() != null) touched.add(c.other().schoolId());
            touched.forEach(school -> conflicts.merge(school, 1, Integer::sum));
            if ("TRAVEL_TIME".equals(c.kind())) travel.addAll(touched);
        }
        return new Counters(conflicts, travel);
    }

    private static SchoolRowResponse row(SchoolRef school, Week week, Counters counters) {
        List<Occurrence> own = week.events().stream().filter(e -> school.id().equals(e.schoolId())).toList();
        return new SchoolRowResponse(school.id(), school.name(), SchoolShortName.of(school.name()),
                counters.conflicts().getOrDefault(school.id(), 0), own.stream().anyMatch(e -> "DRAFT".equals(e.batchStatus())), own.size());
    }

    private static boolean matchesQuick(SchoolRowResponse r, String quick, Set<String> travel) {
        if (quick == null || quick.isBlank()) return true;
        return switch (quick) {
            case "CONFLICT" -> r.conflictCount() > 0;
            case "TRAVEL" -> travel.contains(r.schoolId());
            case "DRAFT" -> r.hasDraft();
            case "EMPTY" -> r.periodCount() == 0;
            default -> throw TeachingScheduleException.badRequest("INVALID_FILTER", "Bộ lọc nhanh không hợp lệ: " + quick);
        };
    }

    public static PeriodTemplateResponse toResponse(PeriodTemplate template) {
        return new PeriodTemplateResponse(template.id(), template.name(), template.periodsPerDay(), template.slots().stream()
                .map(s -> new PeriodSlotResponse(s.periodNo(), s.session(), s.start() == null ? null : s.start().format(HH_MM),
                        s.end() == null ? null : s.end().format(HH_MM))).toList());
    }
}
