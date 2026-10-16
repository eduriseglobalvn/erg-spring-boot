package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.calendar.CalendarReferenceMaps;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Batch-loads reference entities required to assemble Calendar workspace responses. */
public final class CalendarReferenceMapsQueryUseCase {

    private final EducationUnitRepository schools;
    private final ClassroomRepository classes;
    private final SubjectRepository subjects;
    private final LevelRepository levels;
    private final UserRepository users;

    public CalendarReferenceMapsQueryUseCase(
            EducationUnitRepository schools,
            ClassroomRepository classes,
            SubjectRepository subjects,
            LevelRepository levels,
            UserRepository users) {
        this.schools = schools;
        this.classes = classes;
        this.subjects = subjects;
        this.levels = levels;
        this.users = users;
    }

    public CalendarReferenceMaps load(
            String tenantId,
            List<String> schoolIds,
            List<CalendarEvent> events,
            List<CalendarEventTeacher> assignments) {
        Set<String> schoolIdSet = new HashSet<>();
        Set<String> classIdSet = new HashSet<>();
        Set<String> subjectIdSet = new HashSet<>();
        Set<String> levelIdSet = new HashSet<>();
        Set<String> userIdSet = new HashSet<>();
        for (CalendarEvent event : events) {
            if (event.getSchoolId() != null) schoolIdSet.add(event.getSchoolId());
            if (event.getAcademicClassId() != null) classIdSet.add(event.getAcademicClassId());
            if (event.getSubjectId() != null) subjectIdSet.add(event.getSubjectId());
            if (event.getLevelId() != null) levelIdSet.add(event.getLevelId());
        }
        for (CalendarEventTeacher assignment : assignments == null
                ? List.<CalendarEventTeacher>of() : assignments) {
            if (assignment.getTeacherUserId() != null) userIdSet.add(assignment.getTeacherUserId());
        }
        List<String> allSchoolIds = new ArrayList<>(schoolIdSet);
        if (schoolIds != null) {
            for (String schoolId : schoolIds) {
                if (!schoolIdSet.contains(schoolId)) allSchoolIds.add(schoolId);
            }
        }
        return new CalendarReferenceMaps(
                allSchoolIds.isEmpty() ? Map.of() : schools.findByIdInOrderByNameAsc(allSchoolIds).stream()
                        .collect(Collectors.toMap(EducationUnit::getId, Function.identity())),
                classIdSet.isEmpty() ? Map.of() : classes.findAllById(classIdSet).stream()
                        .collect(Collectors.toMap(Classroom::getId, Function.identity())),
                subjectIdSet.isEmpty() ? Map.of() : subjects.findAllById(subjectIdSet).stream()
                        .collect(Collectors.toMap(Subject::getId, Function.identity())),
                levelIdSet.isEmpty() ? Map.of() : levels.findAllById(levelIdSet).stream()
                        .collect(Collectors.toMap(Level::getId, Function.identity())),
                userIdSet.isEmpty() ? Map.of() : users.findAllById(userIdSet).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity())),
                Map.of());
    }
}
