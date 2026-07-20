package com.erg.ergspringboot.service.calendar;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.model.calendar.CalendarRoom;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CalendarExpansionService {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public record ExpansionResult(
            List<CalendarEvent> events,
            List<CalendarEventTeacher> assignments,
            Map<String, String> clientRowIdByEventId
    ) {
        public ExpansionResult(List<CalendarEvent> events, List<CalendarEventTeacher> assignments) {
            this(events, assignments, Map.of());
        }
    }

    public record ReferenceData(
            Map<String, Classroom> classMap,
            Map<String, Subject> subjectMap,
            Map<String, Level> levelMap,
            Map<String, User> userMap,
            Map<String, EducationUnit> schoolMap
    ) {}

    public List<LocalDate> calculateDates(LocalDate applyFrom, int weekday, int repeatWeeks) {
        if (repeatWeeks < 1) {
            throw new IllegalArgumentException("repeatWeeks must be >= 1, got: " + repeatWeeks);
        }
        if (weekday < 1 || weekday > 7) {
            throw new IllegalArgumentException("weekday must be between 1 and 7, got: " + weekday);
        }

        DayOfWeek targetDay = DayOfWeek.of(weekday);
        LocalDate firstDate = applyFrom;
        while (firstDate.getDayOfWeek() != targetDay) {
            firstDate = firstDate.plusDays(1);
        }

        List<LocalDate> dates = new ArrayList<>(repeatWeeks);
        for (int i = 0; i < repeatWeeks; i++) {
            dates.add(firstDate.plusWeeks(i));
        }
        return dates;
    }

    public ExpansionResult expand(
            CalendarBulkRowRequest row,
            String batchId,
            String tenantId,
            String schoolId,
            String createdBy,
            LocalDate applyFrom,
            int repeatWeeks,
            ReferenceData refData
    ) {
        List<CalendarEvent> events = new ArrayList<>();
        List<CalendarEventTeacher> assignments = new ArrayList<>();
        Map<String, String> clientRowIdByEventId = new LinkedHashMap<>();

        LocalTime startTime = LocalTime.parse(row.startTime(), DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime endTime = LocalTime.parse(row.endTime(), DateTimeFormatter.ofPattern("HH:mm"));

        String subjectName = resolveSubjectName(row.subjectId(), refData);
        List<LocalDate> dates = calculateDates(applyFrom, row.weekday(), repeatWeeks);

        for (String classId : row.classIds()) {
            String className = resolveClassName(classId, refData);
            String title = buildTitle(subjectName, className);

            for (LocalDate date : dates) {
                OffsetDateTime startAt = date.atTime(startTime).atZone(VIETNAM_ZONE).toOffsetDateTime();
                OffsetDateTime endAt = date.atTime(endTime).atZone(VIETNAM_ZONE).toOffsetDateTime();

                CalendarEvent event = new CalendarEvent();
                event.setId(IdGenerator.newId());
                event.setTenantId(tenantId);
                event.setBatchId(batchId);
                event.setSchoolId(schoolId);
                event.setAcademicClassId(classId);
                event.setSubjectId(row.subjectId());
                event.setLevelId(row.levelId());
                event.setRoomId(row.roomId());
                event.setRoomName(row.roomName());
                event.setTitle(title);
                event.setStartAt(startAt);
                event.setEndAt(endAt);
                event.setWeekday((short) row.weekday());
                event.setSession(row.session());
                event.setPeriodStart((short) row.periodStart());
                event.setPeriodCount((short) row.periodCount());
                event.setStatus("DRAFT");
                event.setNote(row.note());
                event.setCreatedBy(createdBy);
                event.setUpdatedBy(createdBy);

                events.add(event);
                clientRowIdByEventId.put(event.getId(), row.clientRowId());

                addTeacherAssignments(event, row.mainTeacherIds(), tenantId, "MAIN", refData, assignments);
                addTeacherAssignments(event, row.assistantTeacherIds(), tenantId, "ASSISTANT", refData, assignments);
            }
        }

        return new ExpansionResult(events, assignments, clientRowIdByEventId);
    }

    public String buildTitle(String subjectName, String className) {
        if (subjectName != null && !subjectName.isBlank() && className != null) {
            return subjectName + " - " + className;
        } else if (className != null) {
            return className;
        } else {
            return "Unknown Class";
        }
    }

    private String resolveSubjectName(String subjectId, ReferenceData refData) {
        if (subjectId != null) {
            Subject subject = refData.subjectMap().get(subjectId);
            if (subject != null) {
                return subject.getName();
            }
        }
        return null;
    }

    private String resolveClassName(String classId, ReferenceData refData) {
        if (classId != null) {
            Classroom classroom = refData.classMap().get(classId);
            if (classroom != null) {
                return classroom.getName();
            }
        }
        return null;
    }

    private void addTeacherAssignments(
            CalendarEvent event,
            List<String> teacherIds,
            String tenantId,
            String role,
            ReferenceData refData,
            List<CalendarEventTeacher> assignments
    ) {
        if (teacherIds == null) {
            return;
        }
        for (String teacherId : teacherIds) {
            CalendarEventTeacher teacher = new CalendarEventTeacher();
            teacher.setTenantId(tenantId);
            teacher.setEventId(event.getId());
            teacher.setTeacherUserId(teacherId);
            teacher.setRole(role);

            if (teacherId != null) {
                User user = refData.userMap().get(teacherId);
                if (user != null) {
                    teacher.setDisplayNameSnapshot(user.getFullName());
                }
            }

            assignments.add(teacher);
        }
    }
}
