package com.erg.ergspringboot.lms.internal.classlog;

import com.erg.ergspringboot.curriculum.persistence.CurriculumProgramRow;
import com.erg.ergspringboot.lms.LessonScheduleReadPort.LessonSlot;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogAbsentee;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogDay;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogLag;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogPeriod;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogStats;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogWeek;
import com.erg.ergspringboot.lms.internal.classlog.PpctCounter.PeriodKey;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.LessonLogEntry;
import com.erg.ergspringboot.lms.persistence.LessonLogEntryRepository;
import com.erg.ergspringboot.lms.persistence.Subject;
import com.erg.ergspringboot.lms.persistence.SubjectRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds the class-log rows (timetable period + saved entry + PPCT + absentees + lesson title) of a set of slots. */
@Component
class LessonLogReader {

    static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** One timetable period with its saved entry (null until the teacher edits or confirms it). */
    record Row(LessonSlot slot, LessonLogEntry entry, LessonLogPeriod period) {}

    private final PpctCounter ppct;
    private final CurriculumLookup curriculum;
    private final LessonLogEntryRepository entries;
    private final ClassroomRepository classes;
    private final SubjectRepository subjects;
    private final JdbcTemplate jdbc;

    LessonLogReader(PpctCounter ppct, CurriculumLookup curriculum, LessonLogEntryRepository entries,
                    ClassroomRepository classes, SubjectRepository subjects, JdbcTemplate jdbc) {
        this.ppct = ppct;
        this.curriculum = curriculum;
        this.entries = entries;
        this.classes = classes;
        this.subjects = subjects;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Row> rows(String tenantId, List<LessonSlot> slots, Instant now) {
        if (slots.isEmpty()) return List.of();
        CurriculumLookup.Scope kct = curriculum.scope(tenantId);
        Map<PeriodKey, Integer> positions = positions(tenantId, slots, kct);
        Map<String, LessonLogEntry> saved = entries.findByTenantIdAndEventIdIn(tenantId,
                        slots.stream().map(LessonSlot::eventId).distinct().toList()).stream()
                .collect(Collectors.toMap(e -> key(e.getEventId(), e.getPeriodNo()), e -> e, (a, b) -> a));
        Map<String, String> classNames = classes.findAllById(slots.stream().map(LessonSlot::classId).distinct().toList())
                .stream().collect(Collectors.toMap(Classroom::getId, Classroom::getName, (a, b) -> a));
        Map<String, String> subjectNames = subjects.findAllById(slots.stream().map(LessonSlot::subjectId).distinct().toList())
                .stream().collect(Collectors.toMap(Subject::getId, Subject::getName, (a, b) -> a));
        Map<String, List<LessonLogAbsentee>> absent = absentees(tenantId, slots);
        Map<String, String> teachers = teacherNames(tenantId, slots);

        List<Row> result = new ArrayList<>();
        for (LessonSlot slot : slots) {
            for (int k = 0; k < slot.periodCount(); k++) {
                int periodNo = slot.periodStart() + k;
                LessonLogEntry entry = saved.get(key(slot.eventId(), (short) periodNo));
                int position = positions.getOrDefault(new PeriodKey(slot.eventId(), periodNo), 1);
                int actual = entry == null ? position : entry.getActualPeriod();
                CurriculumProgramRow lesson = kct.framework(slot.subjectId(), slot.levelId(), slot.date()).get(actual);
                result.add(new Row(slot, entry, new LessonLogPeriod(slot.eventId(), slot.date(), periodNo, slot.classId(),
                        classNames.getOrDefault(slot.classId(), ""), slot.subjectId(), subjectNames.getOrDefault(slot.subjectId(), ""),
                        teacherName(slot, teachers),
                        position, actual, lesson == null ? null : lesson.getTitle(),
                        absent.getOrDefault(absentKey(slot.classId(), slot.subjectId(), slot.date(), periodNo), List.of()),
                        entry == null ? null : entry.getComment(), entry == null ? "PENDING" : entry.getStatus(),
                        entry != null && entry.isLate(), happened(slot, periodNo, now),
                        entry == null ? 0 : entry.getVersion().intValue())));
            }
        }
        result.sort(Comparator.comparing((Row r) -> r.period().date()).thenComparingInt(r -> r.period().periodNo())
                .thenComparing(r -> r.period().className()));
        return result;
    }

    /** Highest KCT period of the slot's program, 0 when the KCT is not set up. */
    int frameworkSize(String tenantId, LessonSlot slot) {
        TreeMap<Integer, CurriculumProgramRow> rows = curriculum.scope(tenantId)
                .framework(slot.subjectId(), slot.levelId(), slot.date());
        return rows.isEmpty() ? 0 : rows.lastKey();
    }

    List<CurriculumProgramRow> frameworkRows(String tenantId, String subjectId, String levelId, LocalDate date) {
        return List.copyOf(curriculum.scope(tenantId).framework(subjectId, levelId, date).values());
    }

    LessonLogWeek week(LocalDate weekStart, List<Row> rows, String session, String status) {
        List<LessonLogPeriod> all = rows.stream().map(Row::period).toList();
        Map<LocalDate, String> sessions = new HashMap<>();
        rows.forEach(r -> sessions.putIfAbsent(r.slot().date(), r.slot().session()));
        List<LessonLogDay> days = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            LocalDate date = weekStart.plusDays(d);
            boolean hasAny = all.stream().anyMatch(p -> p.date().equals(date));
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (weekend && !hasAny) continue;
            List<LessonLogPeriod> periods = all.stream().filter(p -> p.date().equals(date))
                    .filter(p -> session == null || session.equalsIgnoreCase(sessions.get(date)))
                    .filter(p -> status == null || status.equals(p.status())).toList();
            days.add(new LessonLogDay(date, sessions.get(date), periods));
        }
        return new LessonLogWeek(weekStart, weekStart.plusDays(6), weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                days, stats(all), lagChip(all));
    }

    static LessonLogStats stats(List<LessonLogPeriod> periods) {
        int taught = (int) periods.stream().filter(LessonLogPeriod::happened).count();
        int confirmed = (int) periods.stream().filter(p -> "CONFIRMED".equals(p.status())).count();
        int pending = (int) periods.stream().filter(p -> p.happened() && !"CONFIRMED".equals(p.status())).count();
        return new LessonLogStats(taught, confirmed, pending);
    }

    /** The class whose PPCT is furthest ahead of its actual period this week (lag of at least 1), or null. */
    static LessonLogLag lagChip(List<LessonLogPeriod> periods) {
        Map<String, LessonLogLag> worst = new LinkedHashMap<>();
        for (LessonLogPeriod p : periods) {
            int lag = p.ppctPeriod() - p.actualPeriod();
            LessonLogLag current = worst.get(p.classId());
            if (lag >= 1 && (current == null || current.lag() < lag)) {
                worst.put(p.classId(), new LessonLogLag(p.classId(), p.className(), lag));
            }
        }
        return worst.values().stream().max(Comparator.comparingInt(LessonLogLag::lag)).orElse(null);
    }

    /**
     * A period is over once its end passed. The timetable holds one start/end per event, so the periods of a multi-period
     * event are spread evenly across it (shortcut: use the period template times when minute accuracy matters).
     */
    static boolean happened(LessonSlot slot, int periodNo, Instant now) {
        if (slot.start() == null || slot.end() == null) return false;
        long perPeriod = Duration.between(slot.start(), slot.end()).toMillis() / Math.max(1, slot.periodCount());
        Instant end = slot.start().plusMillis(perPeriod * (periodNo - slot.periodStart() + 1L));
        return !end.isAfter(now);
    }

    static String key(String eventId, Short periodNo) {
        return eventId + '#' + periodNo;
    }

    static Optional<Row> find(List<Row> rows, String eventId, int periodNo) {
        return rows.stream().filter(r -> r.slot().eventId().equals(eventId) && r.period().periodNo() == periodNo).findFirst();
    }

    /** Vietnamese given name: the last word of the full name. */
    static String shortName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String trimmed = fullName.trim();
        return trimmed.substring(trimmed.lastIndexOf(' ') + 1);
    }

    private static String absentKey(String classId, String subjectId, LocalDate date, int lessonIndex) {
        return classId + "|" + subjectId + "|" + date + "|" + lessonIndex;
    }

    /** PPCT position per period: one count query per distinct plan start date. */
    private Map<PeriodKey, Integer> positions(String tenantId, List<LessonSlot> slots, CurriculumLookup.Scope kct) {
        Map<LocalDate, Set<String>> classIds = new HashMap<>();
        Map<LocalDate, Set<String>> subjectIds = new HashMap<>();
        Map<LocalDate, LocalDate> last = new HashMap<>();
        for (LessonSlot s : slots) {
            LocalDate start = kct.planStart(s.schoolId(), s.subjectId(), s.date());
            classIds.computeIfAbsent(start, k -> new HashSet<>()).add(s.classId());
            subjectIds.computeIfAbsent(start, k -> new HashSet<>()).add(s.subjectId());
            last.merge(start, s.date(), (a, b) -> a.isAfter(b) ? a : b);
        }
        Map<PeriodKey, Integer> result = new HashMap<>();
        last.forEach((start, to) -> result.putAll(ppct.positions(tenantId, classIds.get(start), subjectIds.get(start), start, to)));
        return result;
    }

    /** Absent (ABSENT or EXCUSED) students per class, subject, day and timetable period, from the attendance records. */
    /** Main teachers of the slots (filled by the class and teacher queries). */
    private Map<String, String> teacherNames(String tenantId, List<LessonSlot> slots) {
        List<String> ids = slots.stream().flatMap(s -> s.mainTeacherIds().stream()).distinct().toList();
        Map<String, String> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(ids);
        jdbc.query("select id, coalesce(full_name, '') from users where tenant_id = ? and id in ("
                + String.join(",", Collections.nCopies(ids.size(), "?")) + ")",
                rs -> { result.put(rs.getString(1), rs.getString(2)); }, args.toArray());
        return result;
    }

    private static String teacherName(LessonSlot slot, Map<String, String> names) {
        String joined = slot.mainTeacherIds().stream().map(id -> names.getOrDefault(id, "")).filter(n -> !n.isBlank())
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }

    private Map<String, List<LessonLogAbsentee>> absentees(String tenantId, List<LessonSlot> slots) {
        Collection<String> classIds = slots.stream().map(LessonSlot::classId).distinct().toList();
        LocalDate from = slots.stream().map(LessonSlot::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = slots.stream().map(LessonSlot::date).max(Comparator.naturalOrder()).orElseThrow();
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(classIds);
        args.add(from);
        args.add(to);
        String marks = String.join(",", Collections.nCopies(classIds.size(), "?"));
        Map<String, List<LessonLogAbsentee>> result = new HashMap<>();
        jdbc.query("""
                select a.class_id, a.subject_id, a.attendance_date, a.lesson_index, a.status, a.student_id, s.full_name
                from lms_attendance_records a
                join lms_students s on s.tenant_id = a.tenant_id and s.id = a.student_id
                where a.tenant_id = ? and a.class_id in (%s) and a.attendance_date between ? and ?
                  and a.status in ('ABSENT', 'EXCUSED')
                order by s.full_name
                """.formatted(marks), rs -> {
            result.computeIfAbsent(absentKey(rs.getString("class_id"), rs.getString("subject_id"),
                            rs.getObject("attendance_date", LocalDate.class), rs.getInt("lesson_index")), k -> new ArrayList<>())
                    .add(new LessonLogAbsentee(rs.getString("student_id"), shortName(rs.getString("full_name")),
                            "EXCUSED".equals(rs.getString("status"))));
        }, args.toArray());
        return result;
    }
}
