package com.erg.ergspringboot.lms.internal.classlog;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.LessonScheduleReadPort;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.CurriculumLessonRow;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogChange;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogPeriod;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogWeek;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.LessonLogChangeRepository;
import com.erg.ergspringboot.lms.persistence.LessonLogEntry;
import com.erg.ergspringboot.lms.persistence.LessonLogEntryRepository;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the electronic class log (CBG B3, B5 day view). */
@Service
public class LessonLogQueryUseCase {

    private final LessonScheduleReadPort schedule;
    private final LessonLogReader reader;
    private final LessonLogEntryRepository entries;
    private final LessonLogChangeRepository changes;
    private final ClassroomRepository classes;
    private final UserDirectoryPort users;
    private final Clock clock;

    public LessonLogQueryUseCase(LessonScheduleReadPort schedule, LessonLogReader reader, LessonLogEntryRepository entries,
                          LessonLogChangeRepository changes, ClassroomRepository classes, UserDirectoryPort users, Clock clock) {
        this.schedule = schedule;
        this.reader = reader;
        this.entries = entries;
        this.changes = changes;
        this.classes = classes;
        this.users = users;
        this.clock = clock;
    }

    /** School of the class, for the caller's class-scope check. */
    @Transactional(readOnly = true)
    public String schoolOf(String tenantId, String classId) {
        return classes.findById(classId).filter(c -> tenantId.equals(c.getTenantId())).map(Classroom::getSchoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found."));
    }

    /** Parses a Monday date; the class log weeks are Monday to Sunday. */
    static LocalDate monday(String value) {
        LocalDate date = date(value);
        if (date.getDayOfWeek() != DayOfWeek.MONDAY) throw new BadRequestException("weekStart must be a Monday.");
        return date;
    }

    static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BadRequestException("Dates must use yyyy-MM-dd.");
        }
    }

    @Transactional(readOnly = true)
    public LessonLogWeek teacherWeek(String tenantId, String teacherUserId, String weekStart, String session, String status) {
        LocalDate monday = monday(weekStart);
        List<LessonLogReader.Row> rows = reader.rows(tenantId,
                schedule.teacherSlots(tenantId, teacherUserId, monday, monday.plusDays(6)), clock.instant());
        return reader.week(monday, rows, session, status);
    }

    /** The class's whole week, whoever teaches it; the caller has checked the class scope. */
    @Transactional(readOnly = true)
    public LessonLogWeek classWeek(String tenantId, String classId, String weekStart) {
        LocalDate monday = monday(weekStart);
        List<LessonLogReader.Row> rows = reader.rows(tenantId,
                schedule.classSlots(tenantId, classId, monday, monday.plusDays(6)), clock.instant());
        return reader.week(monday, rows, null, null);
    }

    /** The caller's periods of one day, flat, for attendance by period. */
    @Transactional(readOnly = true)
    public List<LessonLogPeriod> teacherDayPeriods(String tenantId, String teacherUserId, String date) {
        LocalDate day = date(date);
        return reader.rows(tenantId, schedule.teacherSlots(tenantId, teacherUserId, day, day), clock.instant())
                .stream().map(LessonLogReader.Row::period).toList();
    }

    /** Edit history ("Nhat ky so") of the week: one class, or the caller's own entries when classId is null. */
    @Transactional(readOnly = true)
    public List<LessonLogChange> changes(String tenantId, String teacherUserId, String weekStart, String classId) {
        LocalDate monday = monday(weekStart);
        LocalDate sunday = monday.plusDays(6);
        List<LessonLogEntry> found = classId == null
                ? entries.findByTenantIdAndTeacherUserIdAndLessonDateBetween(tenantId, teacherUserId, monday, sunday)
                : entries.findByTenantIdAndClassIdAndLessonDateBetween(tenantId, classId, monday, sunday);
        if (found.isEmpty()) return List.of();
        Map<String, LessonLogEntry> byId = found.stream().collect(Collectors.toMap(LessonLogEntry::getId, Function.identity()));
        var rows = changes.findByTenantIdAndEntryIdInOrderByCreatedAtDesc(tenantId, byId.keySet());
        Map<String, String> actors = users.findByTenantIdAndIds(tenantId, rows.stream().map(c -> c.getActorId()).distinct().toList())
                .stream().collect(Collectors.toMap(UserAccountProjection::id, UserAccountProjection::fullName, (a, b) -> a));
        Map<String, String> classNames = classes.findAllById(found.stream().map(LessonLogEntry::getClassId).distinct().toList())
                .stream().collect(Collectors.toMap(Classroom::getId, Classroom::getName, (a, b) -> a));
        return rows.stream().map(c -> {
            LessonLogEntry e = byId.get(c.getEntryId());
            return new LessonLogChange(c.getId(), c.getCreatedAt().toString(), c.getActorId(), actors.get(c.getActorId()),
                    e.getClassId(), classNames.getOrDefault(e.getClassId(), ""), e.getLessonDate(), e.getPeriodNo(),
                    c.getField(), c.getOldValue(), c.getNewValue());
        }).toList();
    }

    /** Read-only curriculum framework rows of the class's subject (the "Khung chuong trinh" popup). */
    @Transactional(readOnly = true)
    public List<CurriculumLessonRow> curriculum(String tenantId, String classId, String subjectId) {
        Classroom classroom = classes.findById(classId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Class not found."));
        // The class stores its grade, not a curriculum level; the level of the class's own timetable events is the source of truth.
        LocalDate today = LocalDate.now(clock.withZone(LessonLogReader.VIETNAM_ZONE));
        String levelId = schedule.classSlots(tenantId, classroom.getId(), today.minusDays(30), today.plusDays(30)).stream()
                .filter(s -> subjectId.equals(s.subjectId()) && s.levelId() != null)
                .map(LessonScheduleReadPort.LessonSlot::levelId).findFirst().orElse(null);
        return reader.frameworkRows(tenantId, classroom.getSchoolId(), classroom.getId(), subjectId, levelId, today).stream()
                .map(r -> new CurriculumLessonRow(r.getTopic(), r.getPeriod(), r.getTitle())).toList();
    }
}
