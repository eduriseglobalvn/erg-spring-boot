package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacher;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.lms.LessonScheduleReadPort;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the class log's timetable port from the published calendar events. The PPCT count and the tenant-wide report read
 * narrow columns, not CalendarEvent entities (a teacher's PPCT spans every period since 1 September).
 */
@Component
@Transactional(value = "postgresTransactionManager", readOnly = true)
class CalendarLessonScheduleAdapter implements LessonScheduleReadPort {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String MAIN = "MAIN";
    private static final int MAX_ID_LIST = 1_000;

    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository eventTeachers;
    private final JdbcTemplate jdbc;

    CalendarLessonScheduleAdapter(CalendarEventRepository events, CalendarEventTeacherRepository eventTeachers, JdbcTemplate jdbc) {
        this.events = events;
        this.eventTeachers = eventTeachers;
        this.jdbc = jdbc;
    }

    @Override
    public List<LessonSlot> publishedSlots(
            String tenantId, Collection<String> classIds, Collection<String> subjectIds, LocalDate from, LocalDate to) {
        if (classIds.isEmpty() || subjectIds.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>(List.of(tenantId));
        args.addAll(classIds);
        args.addAll(subjectIds);
        args.add(start(from));
        args.add(end(to));
        return jdbc.query("""
                select id, academic_class_id, subject_id, start_at, period_start, period_count
                from teaching_schedule_events
                where tenant_id = ? and status = 'PUBLISHED' and academic_class_id in (%s) and subject_id in (%s)
                  and start_at >= ? and start_at < ?
                order by start_at
                """.formatted(marks(classIds.size()), marks(subjectIds.size())), (rs, row) -> new LessonSlot(rs.getString(1),
                        rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class).atZoneSameInstant(VIETNAM_ZONE).toLocalDate(),
                        rs.getShort(5), rs.getShort(6)), args.toArray());
    }

    @Override
    public List<LessonSlot> teacherSlots(String tenantId, String teacherUserId, LocalDate from, LocalDate to) {
        List<CalendarEvent> found = events.findTeacherCalendarEvents(
                tenantId, teacherUserId, end(to), start(from), List.of("PUBLISHED"));
        Map<String, List<String>> mains = mainTeachers(tenantId, found);
        return found.stream()
                .filter(e -> mains.getOrDefault(e.getId(), List.of()).contains(teacherUserId))
                .map(e -> slot(e, mains.get(e.getId())))
                .toList();
    }

    @Override
    public List<LessonSlot> classSlots(String tenantId, String classId, LocalDate from, LocalDate to) {
        List<CalendarEvent> found = events.findPublishedForClass(tenantId, classId, start(from), end(to));
        Map<String, List<String>> mains = mainTeachers(tenantId, found);
        return found.stream().map(e -> slot(e, mains.getOrDefault(e.getId(), List.of()))).toList();
    }

    @Override
    public List<LessonSlot> publishedWeekSlots(String tenantId, LocalDate from, LocalDate to) {
        return publishedWeekSlots(tenantId, from, to, null, null);
    }

    @Override
    public List<LessonSlot> publishedWeekSlots(
            String tenantId, LocalDate from, LocalDate to, Collection<String> schoolIds, String subjectId) {
        if (schoolIds != null && schoolIds.isEmpty()) return List.of();
        List<Object> args = new ArrayList<>(List.of(tenantId, start(from), end(to)));
        String filter = "";
        if (schoolIds != null) {
            filter += " and e.school_id in (" + marks(schoolIds.size()) + ")";
            args.addAll(schoolIds);
        }
        if (subjectId != null) {
            filter += " and e.subject_id = ?";
            args.add(subjectId);
        }
        // MAIN teachers come with the event, so no IN list of event ids exists to outgrow PostgreSQL's 32,767 bind parameters.
        return jdbc.query("""
                select e.id, e.academic_class_id, e.subject_id, e.start_at, e.end_at, e.period_start, e.period_count,
                       e.school_id, e.level_id, e.session,
                       (select string_agg(t.teacher_user_id, ',') from teaching_schedule_event_teachers t
                         where t.tenant_id = e.tenant_id and t.event_id = e.id and t.role = 'MAIN') as mains
                from teaching_schedule_events e
                where e.tenant_id = ? and e.status = 'PUBLISHED' and e.start_at >= ? and e.start_at < ?%s
                order by e.start_at
                """.formatted(filter), (rs, row) -> {
            OffsetDateTime startAt = rs.getObject(4, OffsetDateTime.class);
            String mains = rs.getString("mains");
            return new LessonSlot(rs.getString(1), rs.getString(2), rs.getString(3),
                    startAt.atZoneSameInstant(VIETNAM_ZONE).toLocalDate(), rs.getShort(6), rs.getShort(7), startAt.toInstant(),
                    rs.getObject(5, OffsetDateTime.class).toInstant(), rs.getString(8), rs.getString(9), rs.getString(10),
                    mains == null ? List.of() : Arrays.asList(mains.split(",")));
        }, args.toArray());
    }

    private Map<String, List<String>> mainTeachers(String tenantId, List<CalendarEvent> found) {
        if (found.isEmpty()) return Map.of();
        List<String> ids = found.stream().map(CalendarEvent::getId).toList();
        List<CalendarEventTeacher> teachers = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += MAX_ID_LIST) {
            teachers.addAll(eventTeachers.findByTenantIdAndEventIdIn(tenantId, ids.subList(i, Math.min(ids.size(), i + MAX_ID_LIST))));
        }
        return teachers.stream()
                .filter(t -> MAIN.equals(t.getRole()))
                .collect(Collectors.groupingBy(CalendarEventTeacher::getEventId,
                        Collectors.mapping(CalendarEventTeacher::getTeacherUserId, Collectors.toList())));
    }

    private static String marks(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static OffsetDateTime start(LocalDate day) {
        return day.atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
    }

    private static OffsetDateTime end(LocalDate lastDay) {
        return lastDay.plusDays(1).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
    }

    private static LessonSlot slot(CalendarEvent e, List<String> mainTeacherIds) {
        return new LessonSlot(e.getId(), e.getAcademicClassId(), e.getSubjectId(),
                e.getStartAt().atZoneSameInstant(VIETNAM_ZONE).toLocalDate(),
                e.getPeriodStart() == null ? 1 : e.getPeriodStart(),
                e.getPeriodCount() == null ? 1 : e.getPeriodCount(),
                e.getStartAt().toInstant(), e.getEndAt().toInstant(), e.getSchoolId(), e.getLevelId(), e.getSession(),
                mainTeacherIds);
    }
}
