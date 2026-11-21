package com.erg.ergspringboot.lms.internal.classlog;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.LessonLogMonthLock;
import com.erg.ergspringboot.lms.LessonScheduleReadPort;
import com.erg.ergspringboot.lms.LessonScheduleReadPort.LessonSlot;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations.AttendanceCommand;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogPeriod;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogStats;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogReader.Row;
import com.erg.ergspringboot.lms.persistence.LessonLogChange;
import com.erg.ergspringboot.lms.persistence.LessonLogChangeRepository;
import com.erg.ergspringboot.lms.persistence.LessonLogEntry;
import com.erg.ergspringboot.lms.persistence.LessonLogEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Write side of the electronic class log: edit a period, confirm a week, save attendance and the log together (CBG B4, B5). */
@Service
public class LessonLogCommandUseCase {

    public record AttendanceMark(String studentId, String status) {}

    private final LessonScheduleReadPort schedule;
    private final LessonLogReader reader;
    private final LessonLogEntryRepository entries;
    private final LessonLogChangeRepository changes;
    private final LessonLogMonthLock monthLock;
    private final LmsAcademicRecordOperations attendance;
    private final Clock clock;

    public LessonLogCommandUseCase(LessonScheduleReadPort schedule, LessonLogReader reader, LessonLogEntryRepository entries,
                            LessonLogChangeRepository changes, LessonLogMonthLock monthLock,
                            LmsAcademicRecordOperations attendance, Clock clock) {
        this.schedule = schedule;
        this.reader = reader;
        this.entries = entries;
        this.changes = changes;
        this.monthLock = monthLock;
        this.attendance = attendance;
        this.clock = clock;
    }

    /** Sets the actual period and comment of one period; creates the entry on the first edit and logs every changed field. */
    @Transactional
    public LessonLogPeriod update(String tenantId, String actorId, String eventId, String date, int periodNo,
                                  int actualPeriod, String comment, Integer expectedVersion) {
        LocalDate day = LessonLogQueryUseCase.date(date);
        Row row = ownPeriod(tenantId, actorId, eventId, day, periodNo);
        // The lesson log has no time gate: a teacher may fill it before the period. Only attendance waits for the period.
        LessonLogEntry entry = apply(tenantId, actorId, row, actualPeriod, comment, expectedVersion, false);
        return reader.refreshed(tenantId, row, entry);
    }

    /** Confirms (reports) every happened, unconfirmed period of the week; closed months are skipped. */
    @Transactional
    public LessonLogStats confirmWeek(String tenantId, String actorId, String weekStart) {
        LocalDate monday = LessonLogQueryUseCase.monday(weekStart);
        Instant now = clock.instant();
        List<Row> rows = reader.rows(tenantId, schedule.teacherSlots(tenantId, actorId, monday, monday.plusDays(6)), now);
        boolean sawClosed = false;
        boolean confirmedAny = false;
        Map<YearMonth, Boolean> closed = new HashMap<>(); // one lock query per month of the week, not per period
        java.util.Set<Row> confirmedNow = new java.util.HashSet<>();
        for (Row row : rows) {
            LessonLogPeriod p = row.period();
            // a period is filled in ahead but confirmed only after it was taught; a week with none taught is a no-op
            if (!p.happened() || "CONFIRMED".equals(p.status())) continue;
            if (closed.computeIfAbsent(YearMonth.from(p.date()), month -> monthLock.isClosed(tenantId, month))) {
                sawClosed = true;
                continue;
            }
            LessonLogEntry entry = row.entry() != null ? row.entry() : newEntry(tenantId, actorId, row);
            entry.setStatus("CONFIRMED");
            entry.setConfirmedAt(now);
            entry.setConfirmedBy(actorId);
            entry.setLate(isLate(p.date(), now));
            entries.save(entry);
            confirmedNow.add(row);
            confirmedAny = true;
        }
        if (sawClosed && !confirmedAny) throw LessonLogException.monthClosed();
        entries.flush();
        // The stats follow from the rows already read: the periods just confirmed count as confirmed, no second pipeline run.
        int taught = 0;
        int confirmed = 0;
        for (Row row : rows) {
            LessonLogPeriod p = row.period();
            boolean isConfirmed = "CONFIRMED".equals(p.status()) || confirmedNow.contains(row);
            if (p.happened()) taught++;
            if (isConfirmed) confirmed++;
        }
        return new LessonLogStats(taught, confirmed, (int) rows.stream()
                .filter(r -> r.period().happened() && !"CONFIRMED".equals(r.period().status()) && !confirmedNow.contains(r)).count());
    }

    /** Saves the attendance of a period and its actual period/comment in one transaction. */
    @Transactional
    public LessonLogPeriod saveAttendanceAndLog(String tenantId, String actorId, String eventId, String date, int periodNo,
                                                List<AttendanceMark> records, int actualPeriod, String comment,
                                                Integer expectedVersion) {
        LocalDate day = LessonLogQueryUseCase.date(date);
        Row row = ownPeriod(tenantId, actorId, eventId, day, periodNo);
        if (!records.isEmpty()) requireStarted(row, periodNo);
        if (monthLock.isClosed(tenantId, YearMonth.from(day))) throw LessonLogException.monthClosed();
        // Validate the log fields first so a bad actual period cannot leave half of the attendance saved.
        checkActual(tenantId, row, actualPeriod);
        LessonSlot slot = row.slot();
        if (!records.isEmpty()) {
            // The students must belong to the class (one query), then one insert-on-conflict for the whole period.
            attendance.assertStudentsInClass(tenantId, slot.classId(), records.stream().map(AttendanceMark::studentId).toList());
            attendance.upsertAttendanceBatch(tenantId, actorId, slot.classId(), slot.subjectId(), day.toString(), periodNo,
                    records.stream().map(m -> new LmsAcademicRecordOperations.AttendanceMark(m.studentId(), m.status())).toList());
        }
        // Month lock and actual period were validated above for this same row; the entry is returned, not reloaded.
        LessonLogEntry entry = apply(tenantId, actorId, row, actualPeriod, comment, expectedVersion, true);
        // Absentees changed with the marks, so they are read again for this one period.
        return reader.refreshed(tenantId, row, entry);
    }

    /** The caller's own MAIN-teacher period, or NOT_TEACHER_OF_PERIOD. */
    private Row ownPeriod(String tenantId, String actorId, String eventId, LocalDate day, int periodNo) {
        List<LessonSlot> slots = schedule.teacherSlots(tenantId, actorId, day, day).stream()
                .filter(s -> s.eventId().equals(eventId)).toList();
        return LessonLogReader.find(reader.rows(tenantId, slots, clock.instant()), eventId, periodNo)
                .orElseThrow(LessonLogException::notTeacherOfPeriod);
    }

    /** A period that has not begun cannot carry attendance (PERIOD_NOT_HAPPENED). */
    private void requireStarted(Row row, int periodNo) {
        if (!LessonLogReader.started(row.slot(), periodNo, clock.instant())) throw LessonLogException.periodNotHappened();
    }

    private void checkActual(String tenantId, Row row, int actualPeriod) {
        int max = reader.frameworkSize(tenantId, row.slot());
        if (actualPeriod < 1 || (max > 0 && actualPeriod > max)) {
            throw LessonLogException.actualPeriodOutOfRange(Math.max(max, 1));
        }
    }

    /** Applies the edit and returns the entry now in effect; {@code validated} skips the month-lock and actual-period checks already done. */
    private LessonLogEntry apply(String tenantId, String actorId, Row row, int actualPeriod, String rawComment, Integer expectedVersion,
                                 boolean validated) {
        LessonLogPeriod p = row.period();
        if (!validated && monthLock.isClosed(tenantId, YearMonth.from(p.date()))) throw LessonLogException.monthClosed();
        if (!validated) checkActual(tenantId, row, actualPeriod);
        if (expectedVersion != null && expectedVersion != p.version()) throw LessonLogException.staleVersion();
        String comment = normalize(rawComment);

        LessonLogEntry entry = row.entry() != null ? row.entry() : newEntry(tenantId, actorId, row);
        boolean isNew = row.entry() == null;
        String oldActual = String.valueOf(p.actualPeriod());
        String oldComment = p.comment();
        boolean actualChanged = actualPeriod != p.actualPeriod();
        boolean commentChanged = !Objects.equals(comment, oldComment);
        if (!isNew && !actualChanged && !commentChanged) return entry;

        entry.setActualPeriod(actualPeriod);
        entry.setComment(comment);
        LessonLogEntry saved = entries.saveAndFlush(entry);
        List<LessonLogChange> log = new ArrayList<>();
        if (actualChanged) log.add(change(tenantId, saved, actorId, "actualPeriod", oldActual, String.valueOf(actualPeriod)));
        if (commentChanged) log.add(change(tenantId, saved, actorId, "comment", oldComment, comment));
        changes.saveAll(log);
        return saved;
    }

    private LessonLogEntry newEntry(String tenantId, String actorId, Row row) {
        LessonLogPeriod p = row.period();
        LessonLogEntry entry = new LessonLogEntry();
        entry.setTenantId(tenantId);
        entry.setEventId(p.eventId());
        entry.setLessonDate(p.date());
        entry.setPeriodNo((short) p.periodNo());
        entry.setClassId(p.classId());
        entry.setSubjectId(row.slot().subjectId());
        entry.setTeacherUserId(actorId);
        entry.setPpctPeriod(p.ppctPeriod());
        entry.setActualPeriod(p.actualPeriod());
        return entry;
    }

    private static LessonLogChange change(String tenantId, LessonLogEntry entry, String actorId, String field,
                                          String oldValue, String newValue) {
        LessonLogChange c = new LessonLogChange();
        c.setTenantId(tenantId);
        c.setEntryId(entry.getId());
        c.setActorId(actorId);
        c.setField(field);
        c.setOldValue(oldValue);
        c.setNewValue(newValue);
        return c;
    }

    private static String normalize(String comment) {
        if (comment == null || comment.isBlank()) return null;
        String trimmed = comment.trim();
        if (trimmed.length() > 500) throw new BadRequestException("The comment exceeds 500 characters.");
        return trimmed;
    }

    /** Confirmation is on time until 23:59 of the teaching day (Asia/Ho_Chi_Minh). */
    static boolean isLate(LocalDate lessonDate, Instant confirmedAt) {
        return !confirmedAt.isBefore(lessonDate.plusDays(1).atStartOfDay(LessonLogReader.VIETNAM_ZONE).toInstant());
    }
}
