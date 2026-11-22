package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.TeacherShortName;
import com.erg.ergspringboot.calendar.TeachingScheduleReuseInput;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.DraftResultResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.HolidayResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ReusePreviewResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ReuseWeekResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.RowIdPairResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TeacherCountResponse;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.TeacherRef;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleWeekAnalyzer.Week;
import com.erg.ergspringboot.calendar.persistence.CalendarBatch;
import com.erg.ergspringboot.calendar.persistence.CalendarBatchRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacher;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Dùng lại tuần" (KE_HOACH_LICH_DAY.md B7): copies the periods of one source week of a school onto other weeks as a new
 * DRAFT batch. Periods that already clash in the source week are not copied; holidays are skipped; periods that would
 * clash in a target week are left out and counted.
 */
@Service
public class ScheduleReuseService {

    static final int MAX_WEEKS = 52;

    /** A target week with the periods that will be created and the numbers shown in the preview table. */
    private record WeekPlan(LocalDate monday, List<Occurrence> toCreate, ReuseWeekResponse response, List<ConflictDetailResponse> warnings) {}

    private record Plan(int sourcePeriods, List<TeacherCountResponse> perTeacher, List<ConflictDetailResponse> excluded,
            List<WeekPlan> weeks, Set<String> copiedSourceIds) {
        int total() {
            return weeks.stream().mapToInt(w -> w.toCreate().size()).sum();
        }
    }

    private final ScheduleDataLoader loader;
    private final ScheduleWeekAnalyzer analyzer;
    private final ScheduleConflictEngine engine;
    private final PeriodTemplateService templates;
    private final TeachingScheduleDraftService drafts;
    private final TeachingScheduleAccessQuery access;
    private final CalendarBatchRepository batches;
    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository eventTeachers;
    private final ObjectProvider<Clock> clock;

    public ScheduleReuseService(ScheduleDataLoader loader, ScheduleWeekAnalyzer analyzer, ScheduleConflictEngine engine,
            PeriodTemplateService templates, TeachingScheduleDraftService drafts, TeachingScheduleAccessQuery access,
            CalendarBatchRepository batches, CalendarEventRepository events, CalendarEventTeacherRepository eventTeachers,
            ObjectProvider<Clock> clock) {
        this.loader = loader;
        this.analyzer = analyzer;
        this.engine = engine;
        this.templates = templates;
        this.drafts = drafts;
        this.access = access;
        this.batches = batches;
        this.events = events;
        this.eventTeachers = eventTeachers;
        this.clock = clock;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public ReusePreviewResponse preview(String tenantId, TeachingScheduleReuseInput in) {
        Plan plan = plan(tenantId, in);
        return new ReusePreviewResponse(plan.sourcePeriods(), plan.perTeacher(), plan.excluded(),
                plan.weeks().stream().map(WeekPlan::response).toList(), plan.total());
    }

    @Transactional("postgresTransactionManager")
    public DraftResultResponse reuse(String tenantId, TeachingScheduleReuseInput in, String actorId) {
        Plan plan = plan(tenantId, in);
        if (plan.total() == 0) {
            throw TeachingScheduleException.badRequest("NOTHING_TO_REUSE", "Không có tiết nào để tạo ở các tuần đã chọn.");
        }
        LocalDate first = plan.weeks().get(0).monday();
        LocalDate last = plan.weeks().get(plan.weeks().size() - 1).monday();
        CalendarBatch batch = drafts.newBatch(tenantId, in.sourceSchoolId(), first, (int) (ChronoUnit.WEEKS.between(first, last) + 1), actorId);
        batch.setPeriodTemplateId(templates.templateForSchool(tenantId, in.sourceSchoolId()).id());
        batches.saveAndFlush(batch);

        Map<String, CalendarEvent> sources = new HashMap<>();
        for (List<String> chunk : ScheduleDataLoader.chunks(plan.copiedSourceIds())) {
            events.findAllById(chunk).forEach(e -> sources.put(e.getId(), e));
        }
        List<CalendarEvent> toSave = new ArrayList<>();
        List<CalendarEventTeacher> links = new ArrayList<>();
        Map<String, String> firstOfRow = new LinkedHashMap<>();
        List<Occurrence> created = new ArrayList<>();
        for (WeekPlan week : plan.weeks()) {
            for (Occurrence o : week.toCreate()) {
                CalendarEvent source = sources.get(o.clientRowId());
                CalendarEvent e = new CalendarEvent();
                e.setId(IdGenerator.newId());
                e.setTenantId(tenantId);
                e.setBatchId(batch.getId());
                e.setSchoolId(batch.getSchoolId());
                e.setAcademicClassId(o.classId());
                e.setSubjectId(source.getSubjectId());
                e.setLevelId(source.getLevelId());
                e.setRoomId(o.roomId());
                e.setRoomName(o.roomName());
                e.setStartAt(o.start());
                e.setEndAt(o.end());
                e.setWeekday((short) o.weekday());
                e.setSession(source.getSession());
                e.setPeriodStart((short) o.periodNo());
                e.setPeriodCount(source.getPeriodCount());
                e.setStatus("DRAFT");
                e.setNote(source.getNote());
                e.setCreatedBy(actorId);
                toSave.add(e);
                o.teachers().forEach(t -> links.add(new CalendarEventTeacher(tenantId, e.getId(), t.id(), t.role(), t.name(), null)));
                firstOfRow.putIfAbsent(o.clientRowId(), e.getId());
                created.add(o);
            }
        }
        events.saveAll(toSave);
        eventTeachers.saveAll(links);
        OffsetDateTime now = OffsetDateTime.now(clock.getIfAvailable(Clock::systemUTC));
        batch.setUpdatedAt(now);
        batches.saveAndFlush(batch);
        List<RowIdPairResponse> rowIdMap = firstOfRow.entrySet().stream().map(x -> new RowIdPairResponse(x.getKey(), x.getValue())).toList();
        List<ConflictDetailResponse> warnings = plan.weeks().stream().flatMap(w -> w.warnings().stream()).toList();
        return new DraftResultResponse(TeachingScheduleDraftService.toResponse(batch, toSave.size()), batch.getRecordVersion().intValue(),
                now.toString(), rowIdMap, warnings, access.accessChanges(tenantId, batch.getSchoolId(), created, now));
    }

    private Plan plan(String tenantId, TeachingScheduleReuseInput in) {
        LocalDate source = ScheduleConflictEngine.weekStart(date(in.sourceWeekStart()));
        TreeSet<LocalDate> targets = targetWeeks(in, source);
        if (loader.names(tenantId, "education_units", "name", Set.of(in.sourceSchoolId())).isEmpty()) {
            throw TeachingScheduleException.notFound("Trường không tồn tại: " + in.sourceSchoolId());
        }
        Week sourceWeek = analyzer.analyze(tenantId, source, List.of(in.sourceSchoolId()));
        Set<String> clashing = sourceWeek.details().stream().filter(c -> "ERROR".equals(c.severity()))
                .map(c -> c.mine().eventId()).collect(Collectors.toSet());
        List<ConflictDetailResponse> excluded = sourceWeek.pairs().stream().filter(c -> "ERROR".equals(c.severity())).toList();
        List<Occurrence> candidates = sourceWeek.events().stream().filter(e -> !clashing.contains(e.eventId())).toList();

        Map<String, int[]> perTeacher = new HashMap<>();
        Map<String, String> teacherNames = new HashMap<>();
        for (Occurrence e : sourceWeek.events()) {
            // periods per main teacher, as the board lists them; an assistant is carried along but not counted here
            for (TeacherRef t : e.teachers().stream().filter(x -> "MAIN".equals(x.role())).toList()) {
                perTeacher.computeIfAbsent(t.id(), k -> new int[1])[0]++;
                teacherNames.put(t.id(), TeacherShortName.of(t.name()));
            }
        }
        List<TeacherCountResponse> counts = perTeacher.entrySet().stream()
                .map(x -> new TeacherCountResponse(x.getKey(), teacherNames.get(x.getKey()), x.getValue()[0]))
                .sorted(Comparator.comparingInt(TeacherCountResponse::periods).reversed().thenComparing(TeacherCountResponse::teacherName)).toList();

        List<WeekPlan> weeks = new ArrayList<>();
        for (LocalDate monday : targets) {
            weeks.add(planWeek(tenantId, in, monday, candidates));
        }
        return new Plan(sourceWeek.events().size(), counts, excluded, weeks,
                candidates.stream().map(Occurrence::eventId).collect(Collectors.toSet()));
    }

    private WeekPlan planWeek(String tenantId, TeachingScheduleReuseInput in, LocalDate monday, List<Occurrence> candidates) {
        Map<LocalDate, ScheduleDataLoader.Holiday> holidays = loader.loadHolidays(tenantId, monday, monday.plusDays(6),
                List.of(in.sourceSchoolId()));
        List<Occurrence> copies = new ArrayList<>();
        int skipped = 0;
        for (Occurrence c : candidates) {
            LocalDate date = monday.plusDays(c.weekday() - 1L);
            if (in.skipHolidays() && holidays.containsKey(date)) {
                skipped++;
                continue;
            }
            OffsetDateTime start = date.atTime(c.start().toLocalTime()).atZone(ScheduleDataLoader.VN).toOffsetDateTime();
            OffsetDateTime end = date.atTime(c.end().toLocalTime()).atZone(ScheduleDataLoader.VN).toOffsetDateTime();
            List<TeacherRef> teachers = in.keepRoomAndAssistant() ? c.teachers()
                    : c.teachers().stream().filter(t -> "MAIN".equals(t.role())).toList();
            // rowKey and clientRowId carry the source event id: that is how a copy finds its source again
            copies.add(new Occurrence(null, c.eventId(), c.eventId(), null, null, "DRAFT", c.schoolId(), c.schoolName(), c.classId(),
                    c.className(), in.keepRoomAndAssistant() ? c.roomId() : null, in.keepRoomAndAssistant() ? c.roomName() : null,
                    c.subjectName(), teachers, date, c.weekday(), c.periodNo(), start, end, null, null, null, true));
        }
        // no fix suggestions: up to 52 weeks are detected and each suggestion costs queries per conflict
        List<ConflictDetailResponse> details = engine.detect(tenantId, copies, Set.of(), false);
        Set<String> blocked = details.stream().filter(d -> "ERROR".equals(d.severity())).map(ConflictDetailResponse::clientRowId)
                .collect(Collectors.toSet());
        List<Occurrence> toCreate = copies.stream().filter(o -> !blocked.contains(o.clientRowId())).toList();
        List<ConflictDetailResponse> errors = details.stream().filter(d -> "ERROR".equals(d.severity())).toList();
        List<ConflictDetailResponse> warnings = details.stream().filter(d -> "WARNING".equals(d.severity())
                && !blocked.contains(d.clientRowId())).toList();
        return new WeekPlan(monday, toCreate, new ReuseWeekResponse(monday.toString(), monday.plusDays(5).toString(), toCreate.size(),
                skipped, blocked.size(), holidays.entrySet().stream().map(h -> new HolidayResponse(h.getKey().toString(), h.getValue().name(), h.getValue().kind())).toList(),
                errors), warnings);
    }

    /** Monday of every week to fill, in order, never the source week itself. */
    private static TreeSet<LocalDate> targetWeeks(TeachingScheduleReuseInput in, LocalDate source) {
        TreeSet<LocalDate> targets = new TreeSet<>();
        if ("SELECTED_WEEKS".equals(in.mode())) {
            if (in.weekStarts() != null) in.weekStarts().forEach(w -> targets.add(ScheduleConflictEngine.weekStart(date(w))));
        } else if ("REPEAT_UNTIL".equals(in.mode())) {
            LocalDate until = date(in.repeatUntil());
            for (LocalDate monday = source.plusWeeks(1); !monday.isAfter(until) && targets.size() <= MAX_WEEKS; monday = monday.plusWeeks(1)) {
                targets.add(monday);
            }
        } else {
            throw TeachingScheduleException.badRequest("INVALID_MODE", "Chế độ dùng lại tuần không hợp lệ: " + in.mode());
        }
        targets.remove(source);
        if (targets.isEmpty()) {
            throw TeachingScheduleException.badRequest("NO_TARGET_WEEKS", "Chọn ít nhất một tuần để tạo.");
        }
        if (targets.size() > MAX_WEEKS || ChronoUnit.WEEKS.between(targets.first(), targets.last()) + 1 > MAX_WEEKS) {
            throw TeachingScheduleException.badRequest("RANGE_TOO_LONG", "Một đợt tối đa " + MAX_WEEKS + " tuần.");
        }
        return targets;
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException | NullPointerException e) {
            throw TeachingScheduleException.badRequest("INVALID_DATE", "Ngày không hợp lệ: " + value);
        }
    }
}
