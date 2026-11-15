package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.TeachingScheduleDraftInput;
import com.erg.ergspringboot.calendar.TeachingScheduleDraftInput.Row;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodTemplate;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleConflictService.DraftRow;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleConflictService.DraftScope;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.persistence.CalendarBatch;
import com.erg.ergspringboot.calendar.persistence.CalendarBatchRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacher;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarRoom;
import com.erg.ergspringboot.calendar.persistence.CalendarRoomRepository;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.BatchResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.DraftResultResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.RowIdPairResponse;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves the grid of a DRAFT batch: the drafted events are replaced by the expansion of {@code rows} in one transaction
 * (the first occurrence of a row keeps the {@code eventId} the client sent), under the batch row lock.
 */
@Service
public class TeachingScheduleDraftService {

    static final int MAX_WEEKS = 52;

    private final CalendarBatchRepository batches;
    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository eventTeachers;
    private final CalendarRoomRepository rooms;
    private final PeriodTemplateService templates;
    private final ScheduleConflictService conflictService;
    private final ScheduleConflictEngine engine;
    private final ScheduleDataLoader loader;
    private final TeachingScheduleAccessQuery access;
    private final ObjectProvider<Clock> clock;

    public TeachingScheduleDraftService(CalendarBatchRepository batches, CalendarEventRepository events,
            CalendarEventTeacherRepository eventTeachers, CalendarRoomRepository rooms, PeriodTemplateService templates,
            ScheduleConflictService conflictService, ScheduleConflictEngine engine, ScheduleDataLoader loader,
            TeachingScheduleAccessQuery access, ObjectProvider<Clock> clock) {
        this.batches = batches;
        this.events = events;
        this.eventTeachers = eventTeachers;
        this.rooms = rooms;
        this.templates = templates;
        this.conflictService = conflictService;
        this.engine = engine;
        this.loader = loader;
        this.access = access;
        this.clock = clock;
    }

    @Transactional("postgresTransactionManager")
    public DraftResultResponse saveDraft(String tenantId, TeachingScheduleDraftInput in, String actorId) {
        LocalDate from = date(in.applyFrom());
        LocalDate until = date(in.repeatUntil());
        if (until.isBefore(from)) {
            throw TeachingScheduleException.badRequest("INVALID_RANGE", "Ngày kết thúc phải sau ngày bắt đầu.");
        }
        int weeks = (int) (ChronoUnit.DAYS.between(from, until) / 7) + 1;
        if (weeks > MAX_WEEKS) {
            throw TeachingScheduleException.badRequest("RANGE_TOO_LONG", "Một đợt tối đa " + MAX_WEEKS + " tuần.");
        }
        validate(in);
        if (loader.names(tenantId, "education_units", "name", Set.of(in.schoolId())).isEmpty()) {
            throw TeachingScheduleException.notFound("Trường không tồn tại: " + in.schoolId());
        }
        PeriodTemplate template = templates.byId(tenantId, in.periodTemplateId());

        CalendarBatch batch = in.batchId() == null ? newBatch(tenantId, in.schoolId(), from, weeks, actorId)
                : existingDraft(tenantId, in);
        requireOwnEventIds(tenantId, batch.getId(), in);
        batch.setApplyFrom(from);
        batch.setRepeatWeeks(weeks);
        batch.setPeriodTemplateId(template.id());
        batches.saveAndFlush(batch);

        List<DraftRow> rows = resolveRooms(tenantId, in, actorId);
        DraftScope scope = new DraftScope(in.schoolId(), batch.getId(), batch.getLabel(), in.subjectId(), from, until, template);
        ScheduleConflictService.Expansion expansion = conflictService.expand(tenantId, scope, rows);
        List<ConflictDetailResponse> conflicts = engine.detect(tenantId, expansion.occurrences(), Set.of(), true);

        events.deleteDraftTeachersByBatch(tenantId, batch.getId());
        events.deleteDraftsByBatch(tenantId, batch.getId());
        List<RowIdPairResponse> rowIdMap = persist(tenantId, batch, in, template, expansion.occurrences(), actorId);

        // a save that changed nothing still moves the version, so a stale second tab is always detected
        OffsetDateTime now = now();
        batch.setUpdatedAt(now);
        batches.saveAndFlush(batch);
        return new DraftResultResponse(toResponse(batch, 0), batch.getRecordVersion().intValue(), now.toString(),
                rowIdMap, conflicts, access.accessChanges(tenantId, batch.getSchoolId(), expansion.occurrences(), now));
    }

    /** Re-read of a stored draft. Rows are keyed by their first event id (clients have no clientRowId any more). */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public DraftResultResponse draft(String tenantId, String batchId) {
        CalendarBatch batch = batches.findByTenantIdAndId(tenantId, batchId)
                .orElseThrow(() -> TeachingScheduleException.notFound("Batch not found: " + batchId));
        Map<String, String> firstEventOfRow = new LinkedHashMap<>();
        loader.loadBatchCandidates(tenantId, batchId, "DRAFT").stream()
                .sorted(java.util.Comparator.comparing(Occurrence::start))
                .forEach(o -> firstEventOfRow.putIfAbsent(o.rowKey(), o.eventId()));
        List<RowIdPairResponse> rowIdMap = firstEventOfRow.values().stream()
                .map(id -> new RowIdPairResponse(id, id)).toList();
        OffsetDateTime now = now();
        return new DraftResultResponse(toResponse(batch, 0), batch.getRecordVersion().intValue(), now.toString(), rowIdMap,
                conflictService.checkStoredDraft(tenantId, batchId), access.accessChanges(tenantId, batch.getSchoolId(),
                loader.loadBatchCandidates(tenantId, batchId, "DRAFT"), now));
    }

    public static BatchResponse toResponse(CalendarBatch batch, int affected) {
        return new BatchResponse(batch.getId(), batch.getSchoolId(), batch.getLabel(), batch.getStatus(),
                batch.getRecordVersion().intValue(), batch.getPeriodTemplateId(), String.valueOf(batch.getApplyFrom()),
                batch.getRepeatWeeks(), batch.getPublishedAt() == null ? null : batch.getPublishedAt().toString(), affected);
    }

    private List<RowIdPairResponse> persist(String tenantId, CalendarBatch batch, TeachingScheduleDraftInput in,
            PeriodTemplate template, List<Occurrence> occurrences, String actorId) {
        Map<String, Row> rowsById = new HashMap<>();
        in.rows().forEach(r -> rowsById.put(r.clientRowId(), r));
        List<CalendarEvent> toSave = new ArrayList<>();
        List<CalendarEventTeacher> links = new ArrayList<>();
        Map<String, String> firstOfRow = new LinkedHashMap<>();
        for (Occurrence o : occurrences) {
            CalendarEvent e = new CalendarEvent();
            e.setId(o.eventId() != null ? o.eventId() : IdGenerator.newId());
            e.setTenantId(tenantId);
            e.setBatchId(batch.getId());
            e.setSchoolId(batch.getSchoolId());
            e.setAcademicClassId(o.classId());
            e.setSubjectId(in.subjectId());
            e.setLevelId(in.levelId());
            e.setRoomId(o.roomId());
            e.setRoomName(o.roomName());
            e.setStartAt(o.start());
            e.setEndAt(o.end());
            e.setWeekday((short) o.weekday());
            e.setSession(templates.slot(template, o.periodNo()).session());
            e.setPeriodStart((short) o.periodNo());
            e.setPeriodCount((short) 1);
            e.setStatus("DRAFT");
            e.setNote(rowsById.get(o.clientRowId()).note());
            e.setCreatedBy(actorId);
            toSave.add(e);
            o.teachers().forEach(t -> links.add(new CalendarEventTeacher(tenantId, e.getId(), t.id(), t.role(), t.name(), null)));
            firstOfRow.putIfAbsent(o.clientRowId(), e.getId());
        }
        events.saveAll(toSave);
        eventTeachers.saveAll(links);
        return firstOfRow.entrySet().stream().map(x -> new RowIdPairResponse(x.getKey(), x.getValue())).toList();
    }

    /** Rows whose room is typed in by name get an existing room of the school (any case) or a new one. */
    private List<DraftRow> resolveRooms(String tenantId, TeachingScheduleDraftInput in, String actorId) {
        Map<String, String> byName = new HashMap<>();
        List<DraftRow> result = new ArrayList<>();
        for (Row r : in.rows()) {
            String roomId = r.roomId();
            String typed = r.newRoomName() == null ? "" : r.newRoomName().trim();
            if (roomId == null && !typed.isEmpty()) {
                roomId = byName.computeIfAbsent(typed.toLowerCase(Locale.ROOT), k -> rooms
                        .findByTenantIdAndSchoolIdAndNameIgnoreCase(tenantId, in.schoolId(), typed).map(CalendarRoom::getId)
                        .orElseGet(() -> createRoom(tenantId, in.schoolId(), typed, actorId)));
            }
            result.add(new DraftRow(r.clientRowId(), r.eventId(), r.weekday(), r.periodNo(), r.classId(), roomId,
                    r.teacherId(), r.assistantId()));
        }
        return result;
    }

    private String createRoom(String tenantId, String schoolId, String name, String actorId) {
        CalendarRoom room = new CalendarRoom();
        room.setTenantId(tenantId);
        room.setSchoolId(schoolId);
        room.setName(name);
        room.setStatus("ACTIVE");
        room.setCreatedBy(actorId);
        return rooms.saveAndFlush(room).getId();
    }

    CalendarBatch newBatch(String tenantId, String schoolId, LocalDate from, int weeks, String actorId) {
        // serialises label picking per school until commit; the unique index stays as the last line of defence
        batches.advisoryXactLock("teaching-schedule-label:" + schoolId);
        Set<String> taken = new HashSet<>();
        batches.findByTenantIdAndSchoolId(tenantId, schoolId).stream()
                .filter(b -> !"CANCELLED".equals(b.getStatus()) && b.getLabel() != null)
                .forEach(b -> taken.add(b.getLabel().toLowerCase(Locale.ROOT)));
        int n = 1;
        while (taken.contains(("Đợt " + n).toLowerCase(Locale.ROOT))) n++;
        CalendarBatch batch = new CalendarBatch();
        batch.setTenantId(tenantId);
        batch.setSchoolId(schoolId);
        batch.setApplyFrom(from);
        batch.setRepeatWeeks(weeks);
        batch.setStatus("DRAFT");
        batch.setLabel("Đợt " + n);
        batch.setCreatedBy(actorId);
        try {
            return batches.saveAndFlush(batch);
        } catch (DataIntegrityViolationException e) {
            throw TeachingScheduleException.conflict("BATCH_LABEL_TAKEN", "Tên đợt vừa được dùng, thử lại.");
        }
    }

    /** A client eventId may only name a DRAFT event of this very batch (it would overwrite or clash with any other). */
    private void requireOwnEventIds(String tenantId, String batchId, TeachingScheduleDraftInput in) {
        List<String> ids = in.rows().stream().map(Row::eventId).filter(java.util.Objects::nonNull).toList();
        if (ids.size() != new HashSet<>(ids).size() || !loader.eventIdsOutsideDraft(tenantId, batchId, ids).isEmpty()) {
            throw TeachingScheduleException.badRequest("EVENT_NOT_IN_BATCH", "Mã tiết không thuộc đợt lịch này.");
        }
    }

    private CalendarBatch existingDraft(String tenantId, TeachingScheduleDraftInput in) {
        CalendarBatch batch = batches.lockByTenantIdAndId(tenantId, in.batchId())
                .orElseThrow(() -> TeachingScheduleException.notFound("Batch not found: " + in.batchId()));
        if (in.expectedVersion() == null) {
            throw TeachingScheduleException.badRequest("EXPECTED_VERSION_REQUIRED", "Thiếu phiên bản đợt lịch; tải lại rồi lưu.");
        }
        if (batch.getRecordVersion() != in.expectedVersion().longValue()) {
            throw TeachingScheduleException.staleVersion();
        }
        if ("CANCELLED".equals(batch.getStatus())) {
            throw TeachingScheduleException.conflict("BATCH_ALREADY_CANCELLED", "Đợt lịch này đã bị hủy.");
        }
        if (!"DRAFT".equals(batch.getStatus())) {
            throw TeachingScheduleException.conflict("BATCH_NOT_DRAFT", "Đợt lịch đã xuất bản, không sửa như bản nháp.");
        }
        if (!batch.getSchoolId().equals(in.schoolId())) {
            throw TeachingScheduleException.badRequest("SCHOOL_MISMATCH", "Đợt lịch thuộc trường khác.");
        }
        return batch;
    }

    private static void validate(TeachingScheduleDraftInput in) {
        if (in.schoolId() == null) throw TeachingScheduleException.badRequest("SCHOOL_REQUIRED", "Chọn trường.");
        if (in.rows() == null) throw TeachingScheduleException.badRequest("ROWS_REQUIRED", "Thiếu danh sách tiết.");
        if (!in.rows().isEmpty() && in.subjectId() == null) {
            throw TeachingScheduleException.badRequest("SUBJECT_REQUIRED", "Chọn môn trước khi lưu lịch.");
        }
        for (Row r : in.rows()) {
            if (r.weekday() < 1 || r.weekday() > 7) {
                throw TeachingScheduleException.badRequest("INVALID_WEEKDAY", "Thứ trong tuần phải từ 1 đến 7.");
            }
            if (r.assistantId() != null && r.assistantId().equals(r.teacherId())) {
                throw TeachingScheduleException.badRequest("ASSISTANT_SAME_AS_TEACHER",
                        "Trợ giảng không được trùng giáo viên chính.");
            }
        }
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
