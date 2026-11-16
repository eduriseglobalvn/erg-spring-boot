package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodSlot;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodTemplate;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.TeacherRef;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entry point of the conflict engine: turns grid rows (or a stored batch) into occurrences and checks them. */
@Service
public class ScheduleConflictService {

    /** One grid row; it repeats weekly from applyFrom to repeatUntil. {@code eventId} is its first occurrence. */
    public record DraftRow(String clientRowId, String eventId, int weekday, int periodNo, String classId, String roomId,
            String teacherId, String assistantId) {}

    public record DraftScope(String schoolId, String batchId, String batchLabel, String subjectId, LocalDate applyFrom,
            LocalDate repeatUntil, PeriodTemplate template) {}

    /** Result of expanding rows: the occurrences plus the dates dropped because they are holidays. */
    record Expansion(List<Occurrence> occurrences, int skippedHolidays) {}

    private final ScheduleConflictEngine engine;
    private final ScheduleDataLoader loader;
    private final PeriodTemplateService templates;

    public ScheduleConflictService(ScheduleConflictEngine engine, ScheduleDataLoader loader, PeriodTemplateService templates) {
        this.engine = engine;
        this.loader = loader;
        this.templates = templates;
    }

    /** Conflicts of a draft that has not been stored (or is about to be replaced). */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public List<ConflictDetailResponse> checkDraft(String tenantId, DraftScope scope, List<DraftRow> rows) {
        return engine.detect(tenantId, expand(tenantId, scope, rows).occurrences(), Set.of(), true);
    }

    /** Conflicts of the DRAFT events a batch currently holds (the publish gate). */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public List<ConflictDetailResponse> checkStoredDraft(String tenantId, String batchId) {
        return checkStoredDraft(tenantId, batchId, true);
    }

    /** The publish gate only counts blocking conflicts, so it passes {@code withSuggestions} false. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public List<ConflictDetailResponse> checkStoredDraft(String tenantId, String batchId, boolean withSuggestions) {
        return engine.detect(tenantId, loader.loadBatchCandidates(tenantId, batchId, "DRAFT"), Set.of(), withSuggestions);
    }

    /** Conflicts of PUBLISHED events of a batch, comparing against everything but themselves (event edit gate). */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public List<ConflictDetailResponse> checkPublishedEvents(String tenantId, String batchId, Set<String> eventIds) {
        List<Occurrence> own = loader.loadBatchCandidates(tenantId, batchId, "PUBLISHED").stream()
                .filter(o -> eventIds.contains(o.eventId())).toList();
        return engine.detect(tenantId, own, eventIds, false);
    }

    /** A room id sent by a client must be an existing room of this school and tenant. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public void requireRoomOfSchool(String tenantId, String schoolId, String roomId) {
        if (loader.names(tenantId, "teaching_schedule_rooms", "name", Set.of(roomId), schoolId).isEmpty()) {
            throw TeachingScheduleException.badRequest("ROOM_INVALID", "Phòng không thuộc trường này");
        }
    }

    /** Every date a row occurs on, minus holidays; slots without a time make the whole call fail. */
    Expansion expand(String tenantId, DraftScope scope, List<DraftRow> rows) {
        Map<LocalDate, ScheduleDataLoader.Holiday> holidays = loader.loadHolidays(tenantId, scope.applyFrom(), scope.repeatUntil(),
                List.of(scope.schoolId()));
        Set<String> classIds = new HashSet<>();
        Set<String> userIds = new HashSet<>();
        Set<String> roomIds = new HashSet<>();
        for (DraftRow row : rows) {
            if (row.classId() == null) throw TeachingScheduleException.badRequest("CLASS_INVALID", "Chọn lớp cho mỗi tiết.");
            classIds.add(row.classId());
            if (row.teacherId() != null) userIds.add(row.teacherId());
            if (row.assistantId() != null) userIds.add(row.assistantId());
            if (row.roomId() != null) roomIds.add(row.roomId());
        }
        // every client id must belong to the tenant (classes and rooms also to the school): never echo another tenant's names
        Map<String, String> classNames = loader.names(tenantId, "academic_classes", "name", classIds, scope.schoolId());
        Map<String, String> userNames = loader.names(tenantId, "users", "full_name", userIds);
        Map<String, String> roomNames = loader.names(tenantId, "teaching_schedule_rooms", "name", roomIds, scope.schoolId());
        requireAll(classIds, classNames, "CLASS_INVALID", "Lớp không thuộc trường này");
        requireAll(userIds, userNames, "TEACHER_INVALID", "Giáo viên không tồn tại");
        requireAll(roomIds, roomNames, "ROOM_INVALID", "Phòng không thuộc trường này");
        String schoolName = loader.names(tenantId, "education_units", "name", Set.of(scope.schoolId())).get(scope.schoolId());
        String subjectName = scope.subjectId() == null ? null
                : loader.names(tenantId, "lms_subjects", "name", Set.of(scope.subjectId())).get(scope.subjectId());
        if (scope.subjectId() != null && subjectName == null) {
            throw TeachingScheduleException.badRequest("SUBJECT_INVALID", "Môn học không tồn tại.");
        }

        List<Occurrence> result = new ArrayList<>();
        int skipped = 0;
        for (DraftRow row : rows) {
            PeriodSlot slot = templates.slot(scope.template(), row.periodNo());
            List<TeacherRef> teachers = new ArrayList<>();
            if (row.teacherId() != null) teachers.add(new TeacherRef(row.teacherId(), userNames.get(row.teacherId()), "MAIN"));
            if (row.assistantId() != null) {
                teachers.add(new TeacherRef(row.assistantId(), userNames.get(row.assistantId()), "ASSISTANT"));
            }
            boolean first = true;
            for (LocalDate date = firstOnOrAfter(scope.applyFrom(), row.weekday()); !date.isAfter(scope.repeatUntil());
                    date = date.plusWeeks(1)) {
                if (holidays.containsKey(date)) {
                    skipped++;
                    continue;
                }
                OffsetDateTime start = date.atTime(slot.start()).atZone(ScheduleDataLoader.VN).toOffsetDateTime();
                OffsetDateTime end = date.atTime(slot.end()).atZone(ScheduleDataLoader.VN).toOffsetDateTime();
                result.add(new Occurrence(first ? row.eventId() : null, row.clientRowId(), row.clientRowId(),
                        scope.batchId(), scope.batchLabel(), "DRAFT", scope.schoolId(), schoolName, row.classId(),
                        classNames.get(row.classId()), row.roomId(), row.roomId() == null ? null : roomNames.get(row.roomId()),
                        subjectName, teachers, date, row.weekday(), row.periodNo(), start, end, null, null, null, true));
                first = false;
            }
        }
        return new Expansion(result, skipped);
    }

    private static void requireAll(Set<String> wanted, Map<String, String> found, String code, String message) {
        if (!found.keySet().containsAll(wanted)) throw TeachingScheduleException.badRequest(code, message);
    }

    private static LocalDate firstOnOrAfter(LocalDate from, int isoWeekday) {
        long ahead = (isoWeekday - from.getDayOfWeek().getValue() + 7) % 7;
        return from.plus(ahead, ChronoUnit.DAYS);
    }
}
