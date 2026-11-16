package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacher;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.lms.LessonScheduleReadPort;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Serves the class log's timetable port from the published calendar events (one event query + one teacher query). */
@Component
@Transactional(value = "postgresTransactionManager", readOnly = true)
class CalendarLessonScheduleAdapter implements LessonScheduleReadPort {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String MAIN = "MAIN";

    private final CalendarEventRepository events;
    private final CalendarEventTeacherRepository eventTeachers;

    CalendarLessonScheduleAdapter(CalendarEventRepository events, CalendarEventTeacherRepository eventTeachers) {
        this.events = events;
        this.eventTeachers = eventTeachers;
    }

    @Override
    public List<LessonSlot> publishedSlots(
            String tenantId, Collection<String> classIds, Collection<String> subjectIds, LocalDate from, LocalDate to) {
        if (classIds.isEmpty() || subjectIds.isEmpty()) return List.of();
        return events.findPublishedForClassesAndSubjects(tenantId, classIds, subjectIds, start(from), end(to)).stream()
                .map(e -> slot(e, List.of()))
                .toList();
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
        List<CalendarEvent> found = events.findPublishedBetween(tenantId, start(from), end(to));
        Map<String, List<String>> mains = mainTeachers(tenantId, found);
        return found.stream().map(e -> slot(e, mains.getOrDefault(e.getId(), List.of()))).toList();
    }

    private Map<String, List<String>> mainTeachers(String tenantId, List<CalendarEvent> found) {
        if (found.isEmpty()) return Map.of();
        return eventTeachers.findByTenantIdAndEventIdIn(tenantId, found.stream().map(CalendarEvent::getId).toList()).stream()
                .filter(t -> MAIN.equals(t.getRole()))
                .collect(Collectors.groupingBy(CalendarEventTeacher::getEventId,
                        Collectors.mapping(CalendarEventTeacher::getTeacherUserId, Collectors.toList())));
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
