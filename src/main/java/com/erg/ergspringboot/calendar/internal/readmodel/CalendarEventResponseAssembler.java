package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarColorGroupResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarTeacherAssignmentResponse;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.service.calendar.CalendarReadService.ReferenceMaps;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/** Converts Calendar event aggregates to the stable API response contract. */
@Service
public class CalendarEventResponseAssembler {

    private static final List<String> COLOR_KEYS = List.of(
            "blue", "green", "orange", "purple", "red", "teal", "pink", "indigo");

    public CalendarEventResponse response(
            CalendarEvent event,
            List<CalendarEventTeacher> teachers,
            ReferenceMaps references) {
        List<CalendarTeacherAssignmentResponse> teacherResponses = teachers.stream()
                .map(teacher -> new CalendarTeacherAssignmentResponse(
                        teacher.getTeacherUserId(),
                        teacher.getDisplayNameSnapshot() != null
                                ? teacher.getDisplayNameSnapshot()
                                : teacherName(teacher.getTeacherUserId(), references),
                        teacher.getRole(),
                        teacher.getColorKey()))
                .toList();
        return new CalendarEventResponse(
                event.getId(), event.getBatchId(), event.getTitle(),
                event.getStartAt() != null ? event.getStartAt().toString() : null,
                event.getEndAt() != null ? event.getEndAt().toString() : null,
                event.getStartAt() != null ? event.getStartAt().toLocalDate().toString() : null,
                event.getStartAt() != null
                        ? event.getStartAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null,
                event.getEndAt() != null
                        ? event.getEndAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null,
                event.getSchoolId(), schoolName(event.getSchoolId(), references),
                event.getAcademicClassId(), className(event.getAcademicClassId(), references),
                event.getSubjectId(), subjectName(event.getSubjectId(), references),
                event.getLevelId(), levelName(event.getLevelId(), references),
                event.getRoomId(), event.getRoomName(),
                event.getPeriodStart() != null ? event.getPeriodStart() : 0,
                event.getPeriodCount() != null ? event.getPeriodCount() : 0,
                teacherResponses, event.getStatus(), event.getNote(), colorGroup(event.getSubjectId()));
    }

    private String schoolName(String id, ReferenceMaps references) {
        EducationUnit value = id == null ? null : references.schoolMap().get(id);
        return value == null ? null : value.getName();
    }

    private String className(String id, ReferenceMaps references) {
        Classroom value = id == null ? null : references.classMap().get(id);
        return value == null ? null : value.getName();
    }

    private String subjectName(String id, ReferenceMaps references) {
        Subject value = id == null ? null : references.subjectMap().get(id);
        return value == null ? null : value.getName();
    }

    private String levelName(String id, ReferenceMaps references) {
        Level value = id == null ? null : references.levelMap().get(id);
        return value == null ? null : value.getName();
    }

    private String teacherName(String id, ReferenceMaps references) {
        User value = id == null ? null : references.userMap().get(id);
        return value == null ? null : value.getFullName();
    }

    private CalendarColorGroupResponse colorGroup(String subjectId) {
        if (subjectId == null) return new CalendarColorGroupResponse("default", null, "subject");
        int index = Math.abs(subjectId.hashCode()) % COLOR_KEYS.size();
        return new CalendarColorGroupResponse(COLOR_KEYS.get(index), null, "subject");
    }
}
