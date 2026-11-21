package com.erg.ergspringboot.timekeeping.internal.teaching;

import static com.erg.ergspringboot.timekeeping.internal.teaching.TeachingSheets.ASSISTANT;
import static com.erg.ergspringboot.timekeeping.internal.teaching.TeachingSheets.MAIN;

import com.erg.ergspringboot.calendar.TeachingPeriodReadPort;
import com.erg.ergspringboot.calendar.TeachingPeriodReadPort.PeriodSlice;
import com.erg.ergspringboot.calendar.TeachingPeriodReadPort.TeachingPeriod;
import com.erg.ergspringboot.lms.LessonLogReportPort;
import com.erg.ergspringboot.lms.LessonLogReportPort.PeriodState;
import com.erg.ergspringboot.lms.LessonScheduleReadPort.LessonSlot;
import com.erg.ergspringboot.timekeeping.TeachingViews.CancelledPeriod;
import com.erg.ergspringboot.timekeeping.TeachingViews.MissingReport;
import com.erg.ergspringboot.timekeeping.TeachingViews.MyTeachingMonth;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingConfirmationView;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingExport;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingMonthRow;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingMonthSheet;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingPeriodPage;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingPeriodRow;
import com.erg.ergspringboot.timekeeping.TeachingViews.TeachingRow;
import com.erg.ergspringboot.timekeeping.TeachingWeek;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import com.erg.ergspringboot.timekeeping.internal.SchoolScope;
import com.erg.ergspringboot.timekeeping.internal.teaching.TeachingSheets.Line;
import com.erg.ergspringboot.timekeeping.internal.teaching.TeachingSheets.Tiet;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingConfirmation;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingConfirmationRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingMonthLine;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingMonthLineRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkTeachingMonthRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the teaching-hours sheets (KE_HOACH_CONG_BAO_GIANG.md B6): the Total sheet, the monthly table and "Cong cua
 * toi". Periods are counted live from the published timetable (cancelled ones never count); a month HR closed is read
 * from its snapshot lines instead, so later timetable edits cannot move it.
 */
@Service
public class TeachingHoursService {

    static final int PAGE_SIZE = 100;
    static final int MAX_EXPORT_PERIODS = 200_000;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm").withZone(TeachingSheets.ZONE);
    private static final String USER_NAMES = "select id, coalesce(full_name, '') from users where tenant_id = :tenant and id in (:ids)";
    private static final String SCHOOL_NAMES = "select id, name from education_units where tenant_id = :tenant and id in (:ids)";
    private static final String SUBJECT_NAMES = "select id, name from curriculum_subjects where tenant_id = :tenant and id in (:ids)";

    private final TeachingPeriodReadPort calendar;
    private final LessonLogReportPort classLog;
    private final TkTeachingMonthRepository months;
    private final TkTeachingConfirmationRepository confirmations;
    private final TkTeachingMonthLineRepository snapshots;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    TeachingHoursService(TeachingPeriodReadPort calendar, LessonLogReportPort classLog, TkTeachingMonthRepository months,
            TkTeachingConfirmationRepository confirmations, TkTeachingMonthLineRepository snapshots,
            NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.calendar = calendar;
        this.classLog = classLog;
        this.months = months;
        this.confirmations = confirmations;
        this.snapshots = snapshots;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public static YearMonth parse(String month) {
        try {
            if (month == null || !month.matches("\\d{4}-\\d{2}")) throw new DateTimeParseException("", "", 0);
            return YearMonth.parse(month);
        } catch (DateTimeParseException invalid) {
            throw new TimekeepingException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "Tháng phải có dạng yyyy-MM");
        }
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(TeachingSheets.ZONE));
    }

    boolean ended(YearMonth month) {
        return today().isAfter(month.atEndOfMonth());
    }

    boolean isClosed(String tenantId, YearMonth month) {
        return months.findByTenantIdAndMonth(tenantId, month.toString()).map(m -> "CLOSED".equals(m.getStatus())).orElse(false);
    }

    /** When HR closed the month, null while it is open. */
    Instant closedAt(String tenantId, YearMonth month) {
        return months.findByTenantIdAndMonth(tenantId, month.toString()).filter(m -> "CLOSED".equals(m.getStatus()))
                .map(m -> m.getClosedAt()).orElse(null);
    }

    List<TeachingPeriod> monthEvents(String tenantId, YearMonth month) {
        return calendar.periods(tenantId, month.atDay(1), month.atEndOfMonth(), TeachingPeriodReadPort.PUBLISHED);
    }

    /** Teacher id to {main total, assistant total} of the live timetable; teachers with no period are absent. */
    @Transactional(readOnly = true)
    public Map<String, int[]> liveTotals(String tenantId, YearMonth month) {
        return totals(monthEvents(tenantId, month));
    }

    /** Same, for one teacher only (the filter runs in SQL). */
    @Transactional(readOnly = true)
    public Map<String, int[]> liveTotals(String tenantId, YearMonth month, String teacherUserId) {
        return totals(calendar.periods(tenantId, month.atDay(1), month.atEndOfMonth(), TeachingPeriodReadPort.PUBLISHED,
                null, teacherUserId));
    }

    public static Map<String, int[]> totals(List<TeachingPeriod> events) {
        Map<String, int[]> totals = new HashMap<>();
        for (TeachingPeriod e : events) {
            totals.computeIfAbsent(e.teacherUserId(), k -> new int[2])[ASSISTANT.equals(e.role()) ? 1 : 0] += e.periodCount();
        }
        return totals;
    }

    // ---- Cong cua toi -------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MyTeachingMonth my(String tenantId, String userId, YearMonth month) {
        List<TeachingWeek> weeks = TeachingWeekSplitter.split(month);
        TeachingConfirmationView confirmation = confirmationView(
                confirmations.findByTenantIdAndMonthAndTeacherUserId(tenantId, month.toString(), userId).orElse(null));
        LocalDate deadline = month.plusMonths(1).atDay(16);
        if (isClosed(tenantId, month)) {
            List<SnapshotLine> mine = snapshotLines(tenantId, month).stream().filter(l -> userId.equals(l.line.teacherId())).toList();
            List<Line> main = mine.stream().filter(l -> MAIN.equals(l.role)).map(l -> l.line).toList();
            List<Line> assistant = mine.stream().filter(l -> ASSISTANT.equals(l.role)).map(l -> l.line).toList();
            return new MyTeachingMonth(month.toString(), "CLOSED", deadline, sum(main), sum(assistant), weeks,
                    snapshotRows(main), snapshotRows(assistant), List.of(), List.of(), confirmation, closedAt(tenantId, month));
        }
        List<TeachingPeriod> mine = calendar.periods(tenantId, month.atDay(1), month.atEndOfMonth(),
                TeachingPeriodReadPort.PUBLISHED, null, userId);
        List<TeachingPeriod> main = TeachingSheets.ofRole(mine, MAIN);
        List<TeachingPeriod> assistant = TeachingSheets.ofRole(mine, ASSISTANT);
        // The port already lists only holiday-cancelled or schedule-cancelled periods (not deleted drafts).
        List<CancelledPeriod> cancelled = calendar.periods(tenantId, month.atDay(1), month.atEndOfMonth(),
                        TeachingPeriodReadPort.CANCELLED, null, userId).stream()
                .map(e -> new CancelledPeriod(e.eventId(),
                        TeachingSheets.dateOf(e), e.className(), e.periodStart(), e.periodCount())).toList();
        return new MyTeachingMonth(month.toString(), status(tenantId, month), deadline,
                main.stream().mapToInt(TeachingPeriod::periodCount).sum(),
                assistant.stream().mapToInt(TeachingPeriod::periodCount).sum(), weeks, rows(main, weeks),
                rows(assistant, weeks), missingReports(tenantId, main), cancelled, confirmation, null);
    }

    /** Periods that already ended and are not confirmed in the class log, one entry per event. */
    private List<MissingReport> missingReports(String tenantId, List<TeachingPeriod> main) {
        if (main.isEmpty()) return List.of();
        Map<String, TeachingPeriod> byEvent = main.stream().collect(Collectors.toMap(TeachingPeriod::eventId, e -> e, (a, b) -> a));
        Set<String> missing = classLog.periodStates(tenantId, slots(byEvent.values()), clock.instant()).stream()
                .filter(s -> s.happened() && !s.confirmed()).map(PeriodState::eventId).collect(Collectors.toSet());
        return byEvent.values().stream().filter(e -> missing.contains(e.eventId()))
                .sorted(Comparator.comparing(TeachingPeriod::start))
                .map(e -> new MissingReport(e.eventId(), TeachingSheets.dateOf(e), e.periodStart(), e.periodCount(),
                        e.className(), HH_MM.format(e.start()), HH_MM.format(e.end()))).toList();
    }

    private static List<TeachingRow> rows(List<TeachingPeriod> events, List<TeachingWeek> weeks) {
        Map<String, List<TeachingPeriod>> groups = events.stream().collect(Collectors.groupingBy(
                e -> e.schoolId() + '|' + e.subjectId() + '|' + e.classId(), LinkedHashMap::new, Collectors.toList()));
        return groups.values().stream().map(g -> {
            TeachingPeriod first = g.get(0);
            int[] perWeek = new int[weeks.size()];
            for (TeachingPeriod e : g) {
                int i = TeachingSheets.weekIndex(weeks, TeachingSheets.dateOf(e));
                if (i >= 0) perWeek[i] += e.periodCount();
            }
            return new TeachingRow(first.schoolName(), first.subjectName(), first.levelName(), first.className(), perWeek,
                    java.util.Arrays.stream(perWeek).sum());
        }).sorted(Comparator.comparing(TeachingRow::schoolName).thenComparing(TeachingRow::subjectName)
                .thenComparing(TeachingRow::className)).toList();
    }

    /** The snapshot keeps one row per school and subject, so the class column carries every class name. */
    private static List<TeachingRow> snapshotRows(List<Line> lines) {
        return lines.stream().map(l -> new TeachingRow(l.schoolName(), l.subjectName(), "", String.join(", ", l.classes()),
                l.weekTotals(), l.total())).sorted(Comparator.comparing(TeachingRow::schoolName)
                .thenComparing(TeachingRow::subjectName)).toList();
    }

    // ---- Monthly table ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TeachingMonthSheet monthSheet(String tenantId, YearMonth month, String role) {
        return sheet(tenantId, month, TeachingSheets.role(role), null, null);
    }

    /** As above for a month still open whose published events the caller already read ({@link #openMonthEvents}). */
    @Transactional(readOnly = true)
    public TeachingMonthSheet monthSheet(String tenantId, YearMonth month, String role, List<TeachingPeriod> events) {
        return sheet(tenantId, month, TeachingSheets.role(role), events, null);
    }

    /** As above showing only the rows (and counting only the teachers) of the schools in {@code scope}. */
    @Transactional(readOnly = true)
    public TeachingMonthSheet monthSheet(String tenantId, SchoolScope scope, YearMonth month, String role,
            List<TeachingPeriod> events) {
        return sheet(tenantId, month, TeachingSheets.role(role), events, scope.schools());
    }

    /** The published events of the month, or null once HR closed it (the sheet is then read from its snapshot). */
    @Transactional(readOnly = true)
    public List<TeachingPeriod> openMonthEvents(String tenantId, YearMonth month) {
        return isClosed(tenantId, month) ? null : monthEvents(tenantId, month);
    }

    /**
     * {@code preloaded} are the month's published events when the caller already read them (export), else null.
     * {@code only} limits the sheet to those schools (null = every school).
     */
    private TeachingMonthSheet sheet(String tenantId, YearMonth month, String role, List<TeachingPeriod> preloaded,
            Set<String> only) {
        List<TeachingWeek> weeks = TeachingWeekSplitter.split(month);
        boolean closed = isClosed(tenantId, month);
        List<TeachingPeriod> all = (closed ? List.<TeachingPeriod>of() : preloaded != null ? preloaded : monthEvents(tenantId, month))
                .stream().filter(e -> only == null || only.contains(e.schoolId())).toList();
        List<Line> lines;
        Set<String> teachers;
        if (closed) {
            List<SnapshotLine> snap = snapshotLines(tenantId, month).stream()
                    .filter(l -> only == null || only.contains(l.line.schoolId())).toList();
            lines = snap.stream().filter(l -> role.equals(l.role)).map(l -> l.line).toList();
            teachers = snap.stream().map(l -> l.line.teacherId()).collect(Collectors.toSet());
        } else {
            lines = TeachingSheets.lines(TeachingSheets.ofRole(all, role), weeks);
            teachers = all.stream().map(TeachingPeriod::teacherUserId).collect(Collectors.toSet());
        }
        Map<String, TkTeachingConfirmation> byTeacher = confirmations.findByTenantIdAndMonth(tenantId, month.toString())
                .stream().collect(Collectors.toMap(TkTeachingConfirmation::getTeacherUserId, c -> c, (a, b) -> a));
        List<TeachingMonthRow> rows = lines.stream().map(l -> {
            TkTeachingConfirmation c = byTeacher.get(l.teacherId());
            return new TeachingMonthRow(l.teacherId(), l.teacherName(), l.schoolName(), l.subjectName(),
                    String.join(", ", l.classes()), l.weekTotals(), l.total(), c == null ? null : c.getIssueNote(),
                    confirmationView(c));
        }).sorted(Comparator.comparing(TeachingMonthRow::schoolName).thenComparing(TeachingMonthRow::teacherName)
                .thenComparing(TeachingMonthRow::subjectName)).toList();
        int confirmed = (int) teachers.stream().filter(t -> byTeacher.get(t) != null && "CONFIRMED".equals(byTeacher.get(t).getStatus())).count();
        String status = closed ? "CLOSED" : ended(month) && !teachers.isEmpty() && confirmed == teachers.size() ? "READY" : "COLLECTING";
        return new TeachingMonthSheet(month.toString(), status, weeks, rows, confirmed, teachers.size(),
                closed ? closedAt(tenantId, month) : null);
    }

    /** CLOSED, else READY once the month is over and every teacher with periods has confirmed, else COLLECTING. */
    private String status(String tenantId, YearMonth month) {
        if (!ended(month)) return "COLLECTING";
        Set<String> done = confirmations.findByTenantIdAndMonth(tenantId, month.toString()).stream()
                .filter(c -> "CONFIRMED".equals(c.getStatus())).map(TkTeachingConfirmation::getTeacherUserId)
                .collect(Collectors.toSet());
        if (done.isEmpty()) return "COLLECTING"; // nobody confirmed yet: no need to scan the tenant for who teaches
        Set<String> teachers = calendar.teacherIds(tenantId, month.atDay(1), month.atEndOfMonth());
        return !teachers.isEmpty() && done.containsAll(teachers) ? "READY" : "COLLECTING";
    }

    // ---- Total sheet and export ---------------------------------------------------------------------------------

    /** One page of the period-by-period sheet; {@code q} matches class, subject, school, teacher and lesson content. */
    @Transactional(readOnly = true)
    public TeachingPeriodPage periods(String tenantId, String role, LocalDate from, LocalDate to, String schoolId,
            String teacherId, String q, int page) {
        return periods(tenantId, SchoolScope.TENANT, role, from, to, schoolId, teacherId, q, page);
    }

    /** {@code scope} limits the schools read: a school outside it is 403, none asked = every school of the scope. */
    @Transactional(readOnly = true)
    public TeachingPeriodPage periods(String tenantId, SchoolScope scope, String role, LocalDate from, LocalDate to,
            String schoolId, String teacherId, String q, int page) {
        String wanted = TeachingSheets.role(role);
        LocalDate start = from != null ? from : LocalDate.of(today().getYear(), 1, 1);
        LocalDate end = to != null ? to : start.withDayOfYear(start.lengthOfYear());
        if (end.isBefore(start)) throw new TimekeepingException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "Khoảng ngày không hợp lệ");
        String needle = blank(q) ? null : q.strip().toLowerCase(Locale.ROOT);
        List<String> schools = scope.schoolsFor(schoolId); // null = every school of the tenant
        if (needle == null && (schools == null || schools.size() == 1)) {
            // paging, counting and ordering run in SQL: a page click no longer expands the whole range
            int pageNo = Math.max(page, 0);
            PeriodSlice slice = calendar.periodSlice(tenantId, start, end, wanted, schools == null ? null : schools.get(0),
                    blank(teacherId) ? null : teacherId, pageNo * PAGE_SIZE, PAGE_SIZE);
            checkExportSize(slice.total());
            List<Tiet> slicePeriods = slice.rows().stream().map(r -> new Tiet(r.event(), r.periodNo(),
                    TeachingSheets.dateOf(r.event()), r.from(), r.to())).toList();
            return new TeachingPeriodPage(periodRows(tenantId, slicePeriods), slice.total(), pageNo, PAGE_SIZE);
        }
        // shortcut: the text filter also matches the lesson content, which only the class log knows, and the calendar port
        // reads one school at a time, so a search (or a caller of several schools) still expands the range; narrow it with
        // schoolId, teacherId or dates (the size cap applies), or give the port a school list / index the titles.
        List<TeachingPeriod> events = TeachingSheets.ofRole(publishedEvents(tenantId, start, end, schools,
                blank(teacherId) ? null : teacherId), wanted);
        checkExportSize(events);
        List<Tiet> tiets = TeachingSheets.expand(events).stream().sorted(Comparator.comparing(Tiet::from)
                .thenComparing(t -> t.event().schoolName()).thenComparing(t -> t.event().className())
                .thenComparingInt(Tiet::periodNo)).toList();
        if (needle == null) {
            int skip = Math.min(Math.max(page, 0) * PAGE_SIZE, tiets.size());
            return new TeachingPeriodPage(periodRows(tenantId, tiets.subList(skip, Math.min(tiets.size(), skip + PAGE_SIZE))),
                    tiets.size(), Math.max(page, 0), PAGE_SIZE);
        }
        List<TeachingPeriodRow> matching = periodRows(tenantId, tiets).stream().filter(r -> matches(r, needle)).toList();
        int skip = Math.min(Math.max(page, 0) * PAGE_SIZE, matching.size());
        return new TeachingPeriodPage(matching.subList(skip, Math.min(matching.size(), skip + PAGE_SIZE)), matching.size(),
                Math.max(page, 0), PAGE_SIZE);
    }

    /** The caller's own periods of one month, what the Total sheet shows filtered to them as {@code role}; read live. */
    @Transactional(readOnly = true)
    public TeachingPeriodPage myPeriods(String tenantId, String userId, YearMonth month, String role, int page) {
        return periods(tenantId, role, month.atDay(1), month.atEndOfMonth(), null, userId, null, page);
    }

    @Transactional(readOnly = true)
    public TeachingExport export(String tenantId, int year) {
        return export(tenantId, year, null, null);
    }

    /** The year (or one {@code month} of it) of one {@code schoolId} or of every school; refuses more than {@link #MAX_EXPORT_PERIODS}. */
    @Transactional(readOnly = true)
    public TeachingExport export(String tenantId, int year, String schoolId, Integer month) {
        return export(tenantId, SchoolScope.TENANT, year, schoolId, month);
    }

    /** As above for the schools of {@code scope}: a school outside it is 403, none asked = every school of the scope. */
    @Transactional(readOnly = true)
    public TeachingExport export(String tenantId, SchoolScope scope, int year, String schoolId, Integer month) {
        List<String> schools = scope.schoolsFor(schoolId);
        if (month != null && (month < 1 || month > 12)) {
            throw new TimekeepingException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "Tháng phải từ 1 đến 12");
        }
        LocalDate from = month == null ? LocalDate.of(year, 1, 1) : LocalDate.of(year, month, 1);
        LocalDate to = month == null ? LocalDate.of(year, 12, 31) : YearMonth.of(year, month).atEndOfMonth();
        Set<String> only = scope.wide() ? null : Set.copyOf(schools);
        List<TeachingPeriod> events = publishedEvents(tenantId, from, to, schools, null);
        checkExportSize(events);
        // shortcut: the filtered range is expanded and its lesson titles read in one go, bounded by MAX_EXPORT_PERIODS.
        List<Tiet> mainPeriods = sorted(TeachingSheets.expand(TeachingSheets.ofRole(events, MAIN)));
        List<Tiet> assistantPeriods = sorted(TeachingSheets.expand(TeachingSheets.ofRole(events, ASSISTANT)));
        Map<String, String> titles = lessonTitles(tenantId, Stream.concat(mainPeriods.stream(), assistantPeriods.stream())
                .map(Tiet::event).distinct().toList());
        List<TeachingPeriodRow> main = periodRows(mainPeriods, titles);
        List<TeachingPeriodRow> assistant = periodRows(assistantPeriods, titles);
        List<TeachingMonthSheet> mainMonths = new ArrayList<>();
        List<TeachingMonthSheet> assistantMonths = new ArrayList<>();
        for (int m = month == null ? 1 : month; m <= (month == null ? 12 : month); m++) {
            YearMonth ym = YearMonth.of(year, m);
            List<TeachingPeriod> inMonth = events.stream().filter(e -> YearMonth.from(TeachingSheets.dateOf(e)).equals(ym)).toList();
            if (inMonth.isEmpty() && !isClosed(tenantId, ym)) continue;
            // a tenant-wide caller keeps the whole closed-month snapshot, as before
            mainMonths.add(sheet(tenantId, ym, MAIN, inMonth, only));
            assistantMonths.add(sheet(tenantId, ym, ASSISTANT, inMonth, only));
        }
        return new TeachingExport(year, main, assistant, mainMonths, assistantMonths);
    }

    /** Published events narrowed to {@code schools} (null = every school); the port reads one school per call. */
    private List<TeachingPeriod> publishedEvents(String tenantId, LocalDate from, LocalDate to, List<String> schools,
            String teacherId) {
        if (schools == null) return calendar.periods(tenantId, from, to, TeachingPeriodReadPort.PUBLISHED, null, teacherId);
        return schools.stream().flatMap(s -> calendar.periods(tenantId, from, to, TeachingPeriodReadPort.PUBLISHED, s,
                teacherId).stream()).toList();
    }

    static void checkExportSize(List<TeachingPeriod> events) {
        checkExportSize(events.stream().mapToLong(e -> Math.max(1, e.periodCount())).sum());
    }

    static void checkExportSize(long periods) {
        if (periods > MAX_EXPORT_PERIODS) {
            throw new TimekeepingException(HttpStatus.BAD_REQUEST, "TEACHING_RANGE_TOO_LARGE",
                    "Quá nhiều tiết (" + periods + "), hãy lọc theo schoolId hoặc month");
        }
    }

    private static List<Tiet> sorted(List<Tiet> tiets) {
        return tiets.stream().sorted(Comparator.comparing(Tiet::from).thenComparingInt(Tiet::periodNo)).toList();
    }

    private List<TeachingPeriodRow> periodRows(String tenantId, List<Tiet> tiets) {
        if (tiets.isEmpty()) return List.of();
        return periodRows(tiets, lessonTitles(tenantId, tiets.stream().map(Tiet::event).distinct().toList()));
    }

    private static List<TeachingPeriodRow> periodRows(List<Tiet> tiets, Map<String, String> titles) {
        return tiets.stream()
.map(t -> new TeachingPeriodRow(t.date(), t.date().getDayOfWeek().getValue(), t.periodNo(),
                t.event().schoolName(), HH_MM.format(t.from()), HH_MM.format(t.to()), t.event().className(),
                t.event().levelName(), t.event().subjectName(), titles.get(t.event().eventId() + '#' + t.periodNo()),
                t.event().teacherName())).toList();
    }

    private Map<String, String> lessonTitles(String tenantId, Collection<TeachingPeriod> events) {
        Map<String, String> titles = new HashMap<>();
        Map<String, TeachingPeriod> unique = events.stream().collect(Collectors.toMap(TeachingPeriod::eventId, e -> e, (a, b) -> a));
        for (PeriodState s : classLog.periodStates(tenantId, slots(unique.values()), clock.instant())) {
            if (s.lessonTitle() != null) titles.put(s.eventId() + '#' + s.periodNo(), s.lessonTitle());
        }
        return titles;
    }

    private static List<LessonSlot> slots(Collection<TeachingPeriod> events) {
        return events.stream().map(e -> new LessonSlot(e.eventId(), e.classId(), e.subjectId(), TeachingSheets.dateOf(e),
                e.periodStart(), e.periodCount(), e.start(), e.end(), e.schoolId(), e.levelId(), null, List.of())).toList();
    }

    private static boolean matches(TeachingPeriodRow r, String needle) {
        return Set.of(nz(r.className()), nz(r.subjectName()), nz(r.schoolName()), nz(r.teacherName()), nz(r.lessonContent()))
                .stream().anyMatch(v -> v.toLowerCase(Locale.ROOT).contains(needle));
    }

    // ---- Snapshot (closed months) -------------------------------------------------------------------------------

    /** A snapshot line with its role. */
    private record SnapshotLine(String role, Line line) {}

    private List<SnapshotLine> snapshotLines(String tenantId, YearMonth month) {
        List<TkTeachingMonthLine> rows = snapshots.findByTenantIdAndMonth(tenantId, month.toString());
        Map<String, String> teachers = names(USER_NAMES, tenantId, rows.stream().map(TkTeachingMonthLine::getTeacherUserId).toList());
        Map<String, String> schools = names(SCHOOL_NAMES, tenantId, rows.stream().map(TkTeachingMonthLine::getSchoolId).toList());
        Map<String, String> subjects = names(SUBJECT_NAMES, tenantId, rows.stream().map(TkTeachingMonthLine::getSubjectId).toList());
        return rows.stream().map(r -> new SnapshotLine(r.getRole(), new Line(r.getTeacherUserId(),
                teachers.getOrDefault(r.getTeacherUserId(), ""), r.getSchoolId(), schools.getOrDefault(r.getSchoolId(), ""),
                r.getSubjectId(), subjects.getOrDefault(r.getSubjectId(), ""),
                new TreeSet<>(r.getClassNames().isBlank() ? List.<String>of() : List.of(r.getClassNames().split(", "))),
                java.util.Arrays.stream(r.getWeekTotals()).mapToInt(Integer::intValue).toArray()))).toList();
    }

    /** The rows HR's close freezes: both role sheets of {@code all}, the month events the close just checked. */
    List<TkTeachingMonthLine> snapshot(String tenantId, YearMonth month, List<TeachingPeriod> all) {
        List<TeachingWeek> weeks = TeachingWeekSplitter.split(month);
        List<TkTeachingMonthLine> result = new ArrayList<>();
        for (String role : List.of(MAIN, ASSISTANT)) {
            for (Line l : TeachingSheets.lines(TeachingSheets.ofRole(all, role), weeks)) {
                TkTeachingMonthLine row = new TkTeachingMonthLine();
                row.setTenantId(tenantId);
                row.setMonth(month.toString());
                row.setTeacherUserId(l.teacherId());
                row.setRole(role);
                row.setSchoolId(l.schoolId());
                row.setSubjectId(l.subjectId());
                row.setClassNames(String.join(", ", l.classes()));
                row.setWeekTotals(java.util.Arrays.stream(l.weekTotals()).boxed().toArray(Integer[]::new));
                row.setTotal(l.total());
                result.add(row);
            }
        }
        return result;
    }

    private Map<String, String> names(String sql, String tenantId, Collection<String> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<String, String> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("tenant", tenantId).addValue("ids", Set.copyOf(ids)),
                rs -> { result.put(rs.getString(1), rs.getString(2)); });
        return result;
    }

    static TeachingConfirmationView confirmationView(TkTeachingConfirmation c) {
        if (c == null) return new TeachingConfirmationView("PENDING", null, null, null, null, 0);
        Instant at = c.getConfirmedAt();
        return new TeachingConfirmationView(c.getStatus(), c.getConfirmedTotal(), c.getConfirmedAssistantTotal(), at,
                c.getIssueNote(), c.getVersion() == null ? 0 : c.getVersion());
    }

    private static int sum(List<Line> lines) {
        return lines.stream().mapToInt(Line::total).sum();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
