package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.CalendarSlotsChanged;
import com.erg.ergspringboot.calendar.CalendarSlotsChanged.RemovedPerson;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ChangedEventResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SlotChangeResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleSlotChanges.Edit;
import com.erg.ergspringboot.calendar.TeachingScheduleSlotChanges.Scope;
import com.erg.ergspringboot.calendar.TeachingScheduleSlotChanges.Swap;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodSlot;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodTemplate;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.TeacherRef;
import com.erg.ergspringboot.calendar.persistence.CalendarBatch;
import com.erg.ergspringboot.calendar.persistence.CalendarBatchRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacher;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edits and teacher swaps of PUBLISHED periods with a scope (this period, this date to the end of the batch, chosen
 * dates). Runs under the tenant publish lock and the batch row lock, so the conflict check against published periods
 * cannot race a publish or another edit. A dry run evaluates everything and writes nothing. No make-up lessons exist:
 * a substitution is just the teacher of the chosen periods changing.
 */
@Service
public class ScheduleSlotChangeService {

    private static final String PUBLISHED = "PUBLISHED";
    static final int MAX_DATES = 60;
    static final int MAX_TEXT = 200;
    private static final Set<String> SWAP_REASONS = Set.of("TIME_CONFLICT", "TEACHER_ON_LEAVE", "REASSIGNMENT");

    private final CalendarBatchRepository batches;
    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository eventTeachers;
    private final PeriodTemplateService templates;
    private final ScheduleConflictService conflictService;
    private final ScheduleConflictEngine engine;
    private final ScheduleDataLoader loader;
    private final ApplicationEventPublisher publisher;
    private final ScheduleSlotChangeAudit audit;
    private final ObjectProvider<Clock> clock;

    public ScheduleSlotChangeService(CalendarBatchRepository batches, CalendarEventRepository events,
            CalendarEventTeacherRepository eventTeachers, PeriodTemplateService templates, ScheduleConflictService conflictService,
            ScheduleConflictEngine engine, ScheduleDataLoader loader, ApplicationEventPublisher publisher, ScheduleSlotChangeAudit audit,
            ObjectProvider<Clock> clock) {
        this.batches = batches;
        this.events = events;
        this.eventTeachers = eventTeachers;
        this.templates = templates;
        this.conflictService = conflictService;
        this.engine = engine;
        this.loader = loader;
        this.publisher = publisher;
        this.audit = audit;
        this.clock = clock;
    }

    /** What changes on one period; null = keep. */
    private record Patch(Integer periodNo, LocalDate newDate, String roomId, String subjectId, String assistantId,
            boolean removeAssistant, String newMainId) {}

    private record Target(CalendarBatch batch, List<CalendarEvent> events) {}

    @Transactional("postgresTransactionManager")
    public SlotChangeResponse edit(String tenantId, Edit in, String actorId) {
        boolean dry = Boolean.TRUE.equals(in.dryRun());
        Scope scope = in.scope();
        boolean singleDate = scope != null && "THIS_ONLY".equals(scope.kind());
        LocalDate newDate = in.newDate() == null ? null : date(in.newDate());
        boolean remove = Boolean.TRUE.equals(in.removeAssistant());
        if (in.periodNo() == null && newDate == null && in.roomId() == null && in.subjectId() == null && in.assistantId() == null
                && !remove) {
            throw TeachingScheduleException.badRequest("NOTHING_TO_CHANGE", "Chưa có thay đổi nào để lưu.");
        }
        if (newDate != null && !singleDate) {
            throw TeachingScheduleException.badRequest("DATE_CHANGE_NEEDS_SINGLE_SCOPE", "Đổi ngày chỉ áp dụng cho một tiết.");
        }
        if (in.assistantId() != null && remove) {
            throw TeachingScheduleException.badRequest("ASSISTANT_CHANGE_AMBIGUOUS", "Chọn trợ giảng mới hoặc bỏ trợ giảng, không cả hai.");
        }
        String reason = text(in.reason(), "REASON_TOO_LONG");
        Patch patch = new Patch(in.periodNo(), newDate, in.roomId(), in.subjectId(), in.assistantId(), remove, null);
        return run(tenantId, in.eventId(), in.expectedVersion(), scope, dry, false, patch, "EDIT", reason, null, actorId);
    }

    @Transactional("postgresTransactionManager")
    public SlotChangeResponse swap(String tenantId, Swap in, String actorId) {
        if (in.newTeacherId() == null || in.newTeacherId().isBlank()) {
            throw TeachingScheduleException.badRequest("TEACHER_REQUIRED", "Chọn giáo viên mới.");
        }
        if (in.reason() != null && !SWAP_REASONS.contains(in.reason())) {
            throw TeachingScheduleException.badRequest("SWAP_REASON_INVALID", "Lý do không hợp lệ: " + in.reason());
        }
        String note = text(in.note(), "NOTE_TOO_LONG");
        Patch patch = new Patch(null, null, null, null, null, false, in.newTeacherId());
        return run(tenantId, in.eventId(), in.expectedVersion(), in.scope(), Boolean.TRUE.equals(in.dryRun()), true, patch, "SWAP",
                in.reason(), note, actorId);
    }

    private SlotChangeResponse run(String tenantId, String eventId, Integer expectedVersion, Scope scope, boolean dry, boolean swap,
            Patch patch, String kind, String reason, String note, String actorId) {
        // the conflict check reads PUBLISHED periods: it must not interleave with a publish or another edit of the tenant
        if (!dry) batches.lockTenantPublish(tenantId);
        Target target = resolve(tenantId, eventId, expectedVersion, scope, dry, swap);
        List<String> ids = target.events().stream().map(CalendarEvent::getId).toList();
        List<Occurrence> before = loader.loadEvents(tenantId, ids);
        List<Occurrence> after = new ArrayList<>();
        PeriodTemplate template = target.batch().getPeriodTemplateId() == null ? null
                : templates.byId(tenantId, target.batch().getPeriodTemplateId());
        Map<String, String> roomNames = patch.roomId() == null ? Map.of() : loader.names(tenantId, "teaching_schedule_rooms", "name",
                Set.of(patch.roomId()), target.batch().getSchoolId());
        Map<String, String> subjectNames = patch.subjectId() == null ? Map.of()
                : loader.names(tenantId, "curriculum_subjects", "name", Set.of(patch.subjectId()));
        Set<String> userIds = new HashSet<>();
        if (patch.assistantId() != null) userIds.add(patch.assistantId());
        if (patch.newMainId() != null) userIds.add(patch.newMainId());
        Map<String, String> userNames = loader.names(tenantId, "users", "full_name", userIds);
        require(patch.roomId() == null || roomNames.containsKey(patch.roomId()), "ROOM_INVALID", "Phòng không thuộc trường này");
        require(patch.subjectId() == null || subjectNames.containsKey(patch.subjectId()), "SUBJECT_INVALID", "Môn học không tồn tại.");
        require(userIds.stream().allMatch(userNames::containsKey), "TEACHER_INVALID", "Giáo viên không tồn tại");
        OffsetDateTime now = now();
        Map<String, Integer> counts = target.events().stream().collect(Collectors.toMap(CalendarEvent::getId, e -> e.getPeriodCount().intValue()));
        for (Occurrence base : before) {
            after.add(moved(tenantId, target.batch(), base, patch, template, roomNames, subjectNames, userNames, now,
                    counts.getOrDefault(base.eventId(), 1)));
        }

        List<ConflictDetailResponse> conflicts = engine.detect(tenantId, after, new HashSet<>(ids), dry);
        boolean blocking = conflicts.stream().anyMatch(c -> "ERROR".equals(c.severity()));
        if (dry) return response(false, blocking, after, null, conflicts);
        if (blocking) {
            long errors = conflicts.stream().filter(c -> "ERROR".equals(c.severity())).count();
            throw new TeachingScheduleException(HttpStatus.CONFLICT, "CHANGE_BLOCKED_BY_CONFLICTS",
                    "Còn " + errors + " xung đột phải xử lý trước khi đổi.", Map.of("conflictCount", (int) errors));
        }

        Map<String, Integer> versions = new LinkedHashMap<>();
        List<RemovedPerson> removed = apply(tenantId, target, before, after, patch, roomNames, subjectNames, actorId, versions);
        String changeId = IdGenerator.newId();
        CalendarEvent anchor = target.events().get(0);
        audit.record(tenantId, actorId, kind, reason, note, scope, target.batch(), ids, before.get(0), after.get(0), changeId, now);
        publisher.publishEvent(new CalendarSlotsChanged("schedule-slots-changed:" + changeId, tenantId, target.batch().getId(), kind,
                ids, removed, actorId, now, 1, anchor.getRecordVersion()));
        return response(true, false, after, versions, conflicts);
    }

    /** Anchor + scope to the periods that change; all are PUBLISHED, of one batch, and still in the future. */
    private Target resolve(String tenantId, String eventId, Integer expectedVersion, Scope scope, boolean dry, boolean swap) {
        CalendarEvent anchor = events.findByTenantIdAndId(tenantId, eventId)
                .orElseThrow(() -> TeachingScheduleException.notFound("Event not found: " + eventId));
        CalendarBatch batch = (dry ? batches.findByTenantIdAndId(tenantId, anchor.getBatchId())
                : batches.lockByTenantIdAndId(tenantId, anchor.getBatchId()))
                .orElseThrow(() -> TeachingScheduleException.notFound("Batch not found: " + anchor.getBatchId()));
        if ("CANCELLED".equals(anchor.getStatus())) {
            throw TeachingScheduleException.conflict("EVENT_CANCELLED", "Tiết này đã bị hủy, không sửa được.");
        }
        if (!PUBLISHED.equals(anchor.getStatus())) {
            throw TeachingScheduleException.conflict("EVENT_NOT_PUBLISHED", "Tiết chưa xuất bản: sửa trong lưới thời khoá biểu.");
        }
        if (expectedVersion != null && anchor.getRecordVersion() != expectedVersion.longValue()) {
            throw TeachingScheduleException.staleVersion();
        }
        String kind = scope == null || scope.kind() == null ? "" : scope.kind();
        List<CalendarEvent> picked;
        switch (kind) {
            case "THIS_ONLY" -> picked = List.of(anchor);
            case "FROM_DATE_TO_END" -> {
                picked = siblings(tenantId, anchor, anchor.getStartAt());
                if (swap) {
                    // a teacher who already covered another day keeps it: only the anchor's own teacher is replaced
                    List<String> pickedIds = new ArrayList<>(picked.stream().map(CalendarEvent::getId).toList());
                    pickedIds.add(anchor.getId());
                    Map<String, String> mains = mainsOf(tenantId, pickedIds);
                    String main = mains.get(anchor.getId());
                    picked = picked.stream().filter(e -> Objects.equals(main, mains.get(e.getId()))).toList();
                }
            }
            case "SELECTED_DATES" -> picked = selected(tenantId, anchor, scope.dates());
            default -> throw TeachingScheduleException.badRequest("SCOPE_INVALID", "Phạm vi áp dụng không hợp lệ.");
        }
        OffsetDateTime now = now();
        if (picked.stream().anyMatch(e -> !e.getStartAt().isAfter(now))) {
            throw TeachingScheduleException.conflict("EVENT_ALREADY_STARTED", "Tiết đã diễn ra, không đổi được.");
        }
        // the anchor leads so that the anchor version feeds the change event
        List<CalendarEvent> ordered = new ArrayList<>(picked);
        ordered.sort((a, b) -> a.getId().equals(anchor.getId()) ? -1 : b.getId().equals(anchor.getId()) ? 1
                : a.getStartAt().compareTo(b.getStartAt()));
        return new Target(batch, ordered);
    }

    private List<CalendarEvent> siblings(String tenantId, CalendarEvent anchor, OffsetDateTime from) {
        return events.findPublishedSiblings(tenantId, anchor.getBatchId(), anchor.getAcademicClassId(), anchor.getSubjectId(),
                anchor.getWeekday(), anchor.getPeriodStart(), from);
    }

    private List<CalendarEvent> selected(String tenantId, CalendarEvent anchor, List<String> rawDates) {
        if (rawDates == null || rawDates.isEmpty() || rawDates.size() > MAX_DATES) {
            throw TeachingScheduleException.badRequest("DATES_INVALID", "Chọn từ 1 đến " + MAX_DATES + " ngày.");
        }
        java.util.TreeSet<LocalDate> dates = rawDates.stream().map(ScheduleSlotChangeService::date)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        OffsetDateTime from = dates.first().atStartOfDay(ScheduleDataLoader.VN).toOffsetDateTime();
        List<CalendarEvent> picked = new ArrayList<>(siblings(tenantId, anchor, from).stream()
                .filter(e -> dates.contains(e.getStartAt().atZoneSameInstant(ScheduleDataLoader.VN).toLocalDate())).toList());
        Set<LocalDate> found = picked.stream().map(e -> e.getStartAt().atZoneSameInstant(ScheduleDataLoader.VN).toLocalDate())
                .collect(Collectors.toSet());
        if (!found.containsAll(dates)) {
            throw TeachingScheduleException.badRequest("DATE_HAS_NO_PERIOD", "Có ngày không có tiết này trong đợt lịch.");
        }
        return picked;
    }

    /** The MAIN teacher of each event (events without one are absent), in one query. */
    private Map<String, String> mainsOf(String tenantId, List<String> eventIds) {
        Map<String, String> mains = new java.util.HashMap<>();
        eventTeachers.findByTenantIdAndEventIdIn(tenantId, eventIds).stream().filter(t -> "MAIN".equals(t.getRole()))
                .forEach(t -> mains.putIfAbsent(t.getEventId(), t.getTeacherUserId()));
        return mains;
    }

    /** The period as it will be after the change: new time, room, subject and people. */
    private Occurrence moved(String tenantId, CalendarBatch batch, Occurrence o, Patch p, PeriodTemplate fixed, Map<String, String> rooms,
            Map<String, String> subjects, Map<String, String> users, OffsetDateTime now, int periodCount) {
        LocalDate date = p.newDate() == null ? o.date() : p.newDate();
        int periodNo = p.periodNo() == null ? o.periodNo() : p.periodNo();
        OffsetDateTime start = o.start();
        OffsetDateTime end = o.end();
        if (p.newDate() != null || p.periodNo() != null) {
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                throw TeachingScheduleException.badRequest("INVALID_WEEKDAY", "Lịch dạy chỉ xếp từ Thứ 2 đến Thứ 7.");
            }
            if (p.newDate() != null && !loader.loadHolidays(tenantId, date, date, List.of(batch.getSchoolId())).isEmpty()) {
                throw TeachingScheduleException.badRequest("DATE_IS_HOLIDAY", "Ngày này là ngày nghỉ.");
            }
            PeriodTemplate template = fixed != null ? fixed : templates.templateForSchool(tenantId, batch.getSchoolId(), date);
            PeriodSlot slot = templates.slot(template, periodNo);
            start = date.atTime(slot.start()).atZone(ScheduleDataLoader.VN).toOffsetDateTime();
            // a multi-period event ends with its last period, not with the first slot
            PeriodSlot last = periodCount > 1 ? templates.slot(template, periodNo + periodCount - 1) : slot;
            end = date.atTime(last.end()).atZone(ScheduleDataLoader.VN).toOffsetDateTime();
            if (!start.isAfter(now)) {
                throw TeachingScheduleException.conflict("EVENT_IN_THE_PAST", "Thời điểm mới đã qua.");
            }
        }
        List<TeacherRef> people = new ArrayList<>(o.teachers());
        if (p.newMainId() != null) {
            if (people.stream().anyMatch(t -> t.id().equals(p.newMainId()) && "ASSISTANT".equals(t.role()))) {
                throw TeachingScheduleException.badRequest("ASSISTANT_SAME_AS_TEACHER",
                        "Giáo viên mới đang là trợ giảng của tiết này; bỏ trợ giảng trước.");
            }
            if (people.stream().anyMatch(t -> t.id().equals(p.newMainId()) && "MAIN".equals(t.role()))) {
                throw TeachingScheduleException.badRequest("SAME_TEACHER", "Giáo viên mới đang dạy tiết này.");
            }
            people.removeIf(t -> "MAIN".equals(t.role()));
            people.add(0, new TeacherRef(p.newMainId(), users.get(p.newMainId()), "MAIN"));
        }
        if (p.assistantId() != null || p.removeAssistant()) {
            people.removeIf(t -> "ASSISTANT".equals(t.role()));
            if (p.assistantId() != null) people.add(new TeacherRef(p.assistantId(), users.get(p.assistantId()), "ASSISTANT"));
        }
        if (people.stream().filter(t -> "MAIN".equals(t.role())).anyMatch(m -> people.stream()
                .anyMatch(a -> "ASSISTANT".equals(a.role()) && a.id().equals(m.id())))) {
            throw TeachingScheduleException.badRequest("ASSISTANT_SAME_AS_TEACHER", "Trợ giảng không được trùng giáo viên chính.");
        }
        // one shared key so a conflict repeated over weeks is reported once ("3/10 tuần")
        return new Occurrence(o.eventId(), "slot-change", "slot-change", o.batchId(), o.batchLabel(), o.batchStatus(), o.schoolId(),
                o.schoolName(), o.classId(), o.className(), p.roomId() == null ? o.roomId() : p.roomId(),
                p.roomId() == null ? o.roomName() : rooms.get(p.roomId()),
                p.subjectId() == null ? o.subjectName() : subjects.get(p.subjectId()), people, date,
                date.getDayOfWeek().getValue(), periodNo, start, end, o.createdByName(), o.createdByRole(), o.createdAt(), true);
    }

    private List<RemovedPerson> apply(String tenantId, Target target, List<Occurrence> before, List<Occurrence> after, Patch p,
            Map<String, String> rooms, Map<String, String> subjects, String actorId, Map<String, Integer> versions) {
        Map<String, Occurrence> afterById = after.stream().collect(Collectors.toMap(Occurrence::eventId, o -> o));
        Map<String, Occurrence> beforeById = before.stream().collect(Collectors.toMap(Occurrence::eventId, o -> o));
        Map<String, List<CalendarEventTeacher>> links = eventTeachers.findByTenantIdAndEventIdIn(tenantId,
                target.events().stream().map(CalendarEvent::getId).toList()).stream()
                .collect(Collectors.groupingBy(CalendarEventTeacher::getEventId));
        PeriodTemplate template = target.batch().getPeriodTemplateId() == null ? null
                : templates.byId(tenantId, target.batch().getPeriodTemplateId());
        List<RemovedPerson> removed = new ArrayList<>();
        List<CalendarEventTeacher> drop = new ArrayList<>();
        List<CalendarEventTeacher> add = new ArrayList<>();
        for (CalendarEvent e : target.events()) {
            Occurrence a = afterById.get(e.getId());
            e.setStartAt(a.start());
            e.setEndAt(a.end());
            e.setWeekday((short) a.weekday());
            e.setPeriodStart((short) a.periodNo());
            if (p.periodNo() != null) {
                PeriodTemplate t = template != null ? template : templates.templateForSchool(tenantId, e.getSchoolId(), a.date());
                e.setSession(templates.slot(t, a.periodNo()).session());
            }
            if (p.roomId() != null) {
                e.setRoomId(p.roomId());
                e.setRoomName(rooms.get(p.roomId()));
            }
            if (p.subjectId() != null) e.setSubjectId(p.subjectId());
            e.setUpdatedBy(actorId);
            Set<String> wanted = a.teachers().stream().map(t -> t.role() + ":" + t.id()).collect(Collectors.toSet());
            Set<String> had = beforeById.get(e.getId()).teachers().stream().map(t -> t.role() + ":" + t.id()).collect(Collectors.toSet());
            for (CalendarEventTeacher link : links.getOrDefault(e.getId(), List.of())) {
                if (!wanted.contains(link.getRole() + ":" + link.getTeacherUserId())) {
                    drop.add(link);
                    removed.add(new RemovedPerson(e.getId(), link.getTeacherUserId(), link.getDisplayNameSnapshot(), link.getRole()));
                }
            }
            for (TeacherRef t : a.teachers()) {
                if (!had.contains(t.role() + ":" + t.id())) add.add(new CalendarEventTeacher(tenantId, e.getId(), t.id(), t.role(), t.name(), null));
            }
        }
        try {
            eventTeachers.deleteAll(drop);
            eventTeachers.flush();
            events.saveAllAndFlush(target.events());
            eventTeachers.saveAllAndFlush(add);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw TeachingScheduleException.staleVersion();
        }
        target.events().forEach(e -> versions.put(e.getId(), e.getRecordVersion().intValue()));
        return removed;
    }

    private static SlotChangeResponse response(boolean applied, boolean blocking, List<Occurrence> after, Map<String, Integer> versions,
            List<ConflictDetailResponse> conflicts) {
        List<ChangedEventResponse> changed = after.stream().sorted(java.util.Comparator.comparing(Occurrence::start))
                .map(o -> new ChangedEventResponse(o.eventId(), o.date().toString(), o.periodNo(),
                        versions == null ? null : versions.get(o.eventId()))).toList();
        return new SlotChangeResponse(applied, blocking, changed.size(), changed, conflicts);
    }

    private static void require(boolean ok, String code, String message) {
        if (!ok) throw TeachingScheduleException.badRequest(code, message);
    }

    private static String text(String raw, String code) {
        String clean = raw == null ? null : raw.trim();
        if (clean != null && clean.isEmpty()) return null;
        if (clean != null && clean.length() > MAX_TEXT) throw TeachingScheduleException.badRequest(code, "Tối đa " + MAX_TEXT + " ký tự.");
        return clean;
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw TeachingScheduleException.badRequest("INVALID_DATE", "Ngày không hợp lệ: " + value);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock.getIfAvailable(Clock::systemUTC));
    }
}
