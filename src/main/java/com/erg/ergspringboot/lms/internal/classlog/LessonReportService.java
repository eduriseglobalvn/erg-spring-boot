package com.erg.ergspringboot.lms.internal.classlog;

import com.erg.ergspringboot.lms.LessonReportNotifier;
import com.erg.ergspringboot.lms.LessonScheduleReadPort;
import com.erg.ergspringboot.lms.LessonScheduleReadPort.LessonSlot;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonReportCell;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonReportMatrix;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonReportRow;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonReportStats;
import com.erg.ergspringboot.lms.persistence.LessonLogEntry;
import com.erg.ergspringboot.lms.persistence.LessonLogEntryRepository;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who has (not) confirmed the class log of the periods they taught (CBG B7): the tracking matrix and the reminders. */
@Service
public class LessonReportService {

    private static final Logger log = LoggerFactory.getLogger(LessonReportService.class);

    private final LessonScheduleReadPort schedule;
    private final LessonLogEntryRepository entries;
    private final UserDirectoryPort users;
    private final LessonReportNotifier notifier;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LessonReportService(LessonScheduleReadPort schedule, LessonLogEntryRepository entries, UserDirectoryPort users,
                               LessonReportNotifier notifier, JdbcTemplate jdbc, Clock clock) {
        this.schedule = schedule;
        this.entries = entries;
        this.users = users;
        this.notifier = notifier;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** One taught (ended) period of one MAIN teacher with its confirmation state. */
    record Taught(String teacherId, String schoolId, LocalDate date, boolean confirmed, boolean late) {}

    /**
     * Teacher x day grid of a Monday-Sunday week. A day with no taught period is NONE; with a taught, unconfirmed period it
     * is MISSING once the day is over (today it is still NONE: the deadline is 23:59); else LATE if any confirmation was late.
     * {@code allowedSchools} null means every school.
     */
    @Transactional(readOnly = true)
    public LessonReportMatrix matrix(String tenantId, String weekStart, String schoolId, String subjectId,
                                     Collection<String> allowedSchools) {
        LocalDate monday = LessonLogQueryUseCase.monday(weekStart);
        List<Taught> taught = taught(tenantId, monday, monday.plusDays(6), schoolId, subjectId, allowedSchools);
        LocalDate today = today();
        Map<String, Map<LocalDate, List<Taught>>> byTeacher = new TreeMap<>();
        Set<LocalDate> daysWithClass = new TreeSet<>();
        for (Taught t : taught) {
            byTeacher.computeIfAbsent(t.teacherId(), k -> new HashMap<>()).computeIfAbsent(t.date(), k -> new ArrayList<>()).add(t);
            daysWithClass.add(t.date());
        }
        List<LocalDate> days = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            LocalDate date = monday.plusDays(d);
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (!weekend || daysWithClass.contains(date)) days.add(date);
        }
        Map<String, String> names = byTeacher.isEmpty() ? Map.of()
                : users.findByTenantIdAndIds(tenantId, byTeacher.keySet()).stream()
                        .collect(Collectors.toMap(UserAccountProjection::id, UserAccountProjection::fullName, (a, b) -> a));
        Map<String, String> schoolNames = schoolNames(tenantId, taught.stream().map(Taught::schoolId).collect(Collectors.toSet()));
        int onTime = 0;
        int late = 0;
        int missing = 0;
        List<LessonReportRow> rows = new ArrayList<>();
        for (var teacher : byTeacher.entrySet()) {
            List<LessonReportCell> cells = new ArrayList<>();
            for (LocalDate date : days) {
                List<Taught> day = teacher.getValue().getOrDefault(date, List.of());
                String state = state(day, date, today);
                int periods = day.size();
                switch (state) {
                    case "ON_TIME" -> onTime += periods;
                    case "LATE" -> late += periods;
                    case "MISSING" -> missing += periods;
                    default -> { }
                }
                cells.add(new LessonReportCell(date, state, periods));
            }
            Set<String> schools = new TreeSet<>();
            teacher.getValue().values().forEach(l -> l.forEach(t -> schools.add(schoolNames.getOrDefault(t.schoolId(), ""))));
            rows.add(new LessonReportRow(teacher.getKey(), names.getOrDefault(teacher.getKey(), ""),
                    String.join(", ", schools), cells));
        }
        rows.sort(Comparator.comparing(LessonReportRow::schoolName).thenComparing(LessonReportRow::teacherName));
        int schools = (int) taught.stream().map(Taught::schoolId).distinct().count();
        return new LessonReportMatrix(monday, days,
                new LessonReportStats(taught.size(), rows.size(), schools, onTime, late, missing), rows);
    }

    static String state(List<Taught> day, LocalDate date, LocalDate today) {
        if (day.isEmpty()) return "NONE";
        if (day.stream().anyMatch(t -> !t.confirmed())) return date.isBefore(today) ? "MISSING" : "NONE";
        return day.stream().anyMatch(Taught::late) ? "LATE" : "ON_TIME";
    }

    /** Teacher id to the number of ended, unconfirmed periods within the days from..to (only past days count). */
    @Transactional(readOnly = true)
    public Map<String, Integer> unconfirmedByTeacher(String tenantId, LocalDate from, LocalDate to, Collection<String> allowedSchools) {
        LocalDate last = to.isBefore(today()) ? to : today().minusDays(1);
        Map<String, Integer> result = new TreeMap<>();
        for (Taught t : taught(tenantId, from, last, null, null, allowedSchools)) {
            if (!t.confirmed()) result.merge(t.teacherId(), 1, Integer::sum);
        }
        return result;
    }

    /** Manual reminder: only teachers of the tenant (and of the caller's schools) who still owe periods of the last 30 days. */
    public int remind(String tenantId, String actorUserId, Collection<String> teacherIds, Collection<String> allowedSchools) {
        LocalDate today = today();
        Map<String, Integer> owed = unconfirmedByTeacher(tenantId, today.minusDays(30), today.minusDays(1), allowedSchools);
        int sent = 0;
        for (String teacherId : new HashSet<>(teacherIds)) {
            Integer count = owed.get(teacherId);
            if (count != null && send(tenantId, actorUserId, "lesson-report-manual:" + teacherId + ":" + today, teacherId,
                    "Nhắc báo giảng", "Bạn còn " + count + " tiết đã dạy chưa xác nhận trong sổ đầu bài.")) sent++;
        }
        return sent;
    }

    boolean send(String tenantId, String actorUserId, String dedupeKey, String teacherId, String title, String body) {
        try {
            notifier.remind(tenantId, actorUserId, dedupeKey, teacherId, title, body);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Lesson report reminder failed for user {}: {}", teacherId, ex.getClass().getSimpleName());
            return false;
        }
    }

    /** Tenants with classes, for the scheduled job (call under the maintenance tenant context). */
    List<String> tenantIds() {
        return jdbc.queryForList("select distinct tenant_id from academic_classes", String.class);
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(LessonLogReader.VIETNAM_ZONE));
    }

    private List<Taught> taught(String tenantId, LocalDate from, LocalDate to, String schoolId, String subjectId,
                                Collection<String> allowedSchools) {
        if (to.isBefore(from)) return List.of();
        List<LessonSlot> slots = schedule.publishedWeekSlots(tenantId, from, to).stream()
                .filter(s -> schoolId == null || schoolId.equals(s.schoolId()))
                .filter(s -> subjectId == null || subjectId.equals(s.subjectId()))
                .filter(s -> allowedSchools == null || allowedSchools.contains(s.schoolId()))
                .toList();
        if (slots.isEmpty()) return List.of();
        Map<String, LessonLogEntry> saved = new HashMap<>();
        entries.findByTenantIdAndEventIdIn(tenantId, slots.stream().map(LessonSlot::eventId).distinct().toList())
                .forEach(e -> saved.putIfAbsent(LessonLogReader.key(e.getEventId(), e.getPeriodNo()), e));
        var now = clock.instant();
        List<Taught> result = new ArrayList<>();
        for (LessonSlot slot : slots) {
            for (int k = 0; k < slot.periodCount(); k++) {
                int periodNo = slot.periodStart() + k;
                if (!LessonLogReader.happened(slot, periodNo, now)) continue;
                LessonLogEntry entry = saved.get(LessonLogReader.key(slot.eventId(), (short) periodNo));
                boolean confirmed = entry != null && "CONFIRMED".equals(entry.getStatus());
                for (String teacher : slot.mainTeacherIds()) {
                    result.add(new Taught(teacher, slot.schoolId(), slot.date(), confirmed, confirmed && entry.isLate()));
                }
            }
        }
        return result;
    }

    private Map<String, String> schoolNames(String tenantId, Set<String> schoolIds) {
        Map<String, String> result = new HashMap<>();
        schoolIds.stream().filter(Objects::nonNull).forEach(id -> jdbc.query(
                "select name from education_units where tenant_id = ? and id = ?", rs -> { result.put(id, rs.getString(1)); },
                tenantId, id));
        return result;
    }
}
