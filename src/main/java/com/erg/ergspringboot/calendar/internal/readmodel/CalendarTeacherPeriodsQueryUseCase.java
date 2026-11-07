package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Subject;
import com.erg.ergspringboot.lms.persistence.SubjectRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Principal-free list of a teacher's published periods (timekeeping views and jobs). */
public final class CalendarTeacherPeriodsQueryUseCase {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CalendarEventRepository events;
    private final ClassroomRepository classes;
    private final SubjectRepository subjects;

    public CalendarTeacherPeriodsQueryUseCase(
            CalendarEventRepository events, ClassroomRepository classes, SubjectRepository subjects) {
        this.events = events;
        this.classes = classes;
        this.subjects = subjects;
    }

    public List<TeacherPeriod> teacherPeriods(String tenantId, String teacherUserId, LocalDate from, LocalDate to) {
        List<CalendarEvent> found = events.findTeacherCalendarEvents(tenantId, teacherUserId,
                to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime(),
                from.atStartOfDay(VIETNAM_ZONE).toOffsetDateTime(),
                List.of("PUBLISHED"));
        if (found.isEmpty()) return List.of();
        Map<String, String> classNames = classes.findAllById(ids(found, CalendarEvent::getAcademicClassId)).stream()
                .collect(Collectors.toMap(Classroom::getId, Classroom::getName, (a, b) -> a));
        Map<String, String> subjectNames = subjects.findAllById(ids(found, CalendarEvent::getSubjectId)).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName, (a, b) -> a));
        return found.stream()
                .map(e -> new TeacherPeriod(e.getStartAt().toInstant(), e.getEndAt().toInstant(),
                        classNames.get(e.getAcademicClassId()), subjectNames.get(e.getSubjectId()), e.getRoomName()))
                .toList();
    }

    public Set<String> teachersStarting(String tenantId, Collection<String> teacherUserIds, Instant from, Instant to) {
        if (teacherUserIds.isEmpty()) return Set.of();
        return new HashSet<>(events.findTeachersWithEventStarting(tenantId, teacherUserIds,
                from.atOffset(ZoneOffset.UTC), to.atOffset(ZoneOffset.UTC), List.of("PUBLISHED")));
    }

    private static Set<String> ids(List<CalendarEvent> found, Function<CalendarEvent, String> id) {
        return found.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
