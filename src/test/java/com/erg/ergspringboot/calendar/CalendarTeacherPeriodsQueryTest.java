package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.internal.readmodel.CalendarTeacherPeriodsQueryUseCase;
import com.erg.ergspringboot.calendar.persistence.CalendarEvent;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacher;
import com.erg.ergspringboot.calendar.persistence.CalendarEventTeacherRepository;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubject;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubjectRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarTeacherPeriodsQueryTest {

    private final CalendarEventRepository events = mock(CalendarEventRepository.class);
    private final CalendarEventTeacherRepository eventTeachers = mock(CalendarEventTeacherRepository.class);
    private final ClassroomRepository classes = mock(ClassroomRepository.class);
    private final CurriculumSubjectRepository subjects = mock(CurriculumSubjectRepository.class);

    @Test
    void publishedPeriodsOfTheTeacherInVietnamDaysWithClassSubjectAndRoom() {
        CalendarEvent event = new CalendarEvent();
        event.setId("event-1");
        event.setSchoolId("school-1");
        event.setLevelId("level-1");
        event.setPeriodStart((short) 3);
        event.setPeriodCount((short) 2);
        event.setAcademicClassId("class-7a");
        event.setSubjectId("math");
        event.setRoomName("P201");
        event.setStartAt(OffsetDateTime.parse("2026-11-12T08:00:00+07:00"));
        event.setEndAt(OffsetDateTime.parse("2026-11-12T08:45:00+07:00"));
        Classroom classroom = new Classroom();
        classroom.setId("class-7a");
        classroom.setName("7A");
        CurriculumSubject subject = new CurriculumSubject();
        subject.setId("math");
        subject.setName("Toán");
        when(events.findTeacherCalendarEvents(any(), any(), any(), any(), any())).thenReturn(List.of(event));
        CalendarEventTeacher assistant = new CalendarEventTeacher();
        assistant.setEventId("event-1");
        assistant.setTeacherUserId("teacher-1");
        assistant.setRole("ASSISTANT");
        when(eventTeachers.findByTenantIdAndEventIdIn(any(), any())).thenReturn(List.of(assistant));
        when(classes.findAllById(any())).thenReturn(List.of(classroom));
        when(subjects.findAllById(any())).thenReturn(List.of(subject));

        List<TeacherPeriod> periods = new CalendarTeacherPeriodsQueryUseCase(events, eventTeachers, classes, subjects)
                .teacherPeriods("tenant-a", "teacher-1", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12));

        assertThat(periods).containsExactly(new TeacherPeriod(
                Instant.parse("2026-11-12T01:00:00Z"), Instant.parse("2026-11-12T01:45:00Z"), "7A", "Toán", "P201",
                "event-1", "class-7a", "math", "school-1", "level-1", 3, 2, "ASSISTANT"));
        // [from 00:00, to next day 00:00) in Asia/Ho_Chi_Minh, published events only.
        verify(events).findTeacherCalendarEvents("tenant-a", "teacher-1",
                OffsetDateTime.of(2026, 11, 13, 0, 0, 0, 0, ZoneOffset.ofHours(7)),
                OffsetDateTime.of(2026, 11, 12, 0, 0, 0, 0, ZoneOffset.ofHours(7)),
                List.of("PUBLISHED"));
    }

    @Test
    void noEventsGivesAnEmptyListWithoutLookups() {
        when(events.findTeacherCalendarEvents(any(), any(), any(), any(), any())).thenReturn(List.of());

        assertThat(new CalendarTeacherPeriodsQueryUseCase(events, eventTeachers, classes, subjects)
                .teacherPeriods("tenant-a", "teacher-1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .isEmpty();
    }
}
