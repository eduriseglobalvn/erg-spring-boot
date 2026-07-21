package com.erg.ergspringboot.calendar.internal.bulk;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import com.erg.ergspringboot.model.calendar.CalendarRoom;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.calendar.CalendarRoomRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService.ReferenceData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Loads and validates the reference snapshot shared by Calendar bulk commands. */
@Service
public class CalendarBulkReferenceDataUseCase {

    private static final String ACTIVE = "ACTIVE";

    private final ObjectProvider<ClassroomRepository> classrooms;
    private final ObjectProvider<SubjectRepository> subjects;
    private final ObjectProvider<LevelRepository> levels;
    private final ObjectProvider<UserRepository> users;
    private final ObjectProvider<CalendarRoomRepository> rooms;
    private final ObjectProvider<EducationUnitRepository> schools;

    @Autowired
    public CalendarBulkReferenceDataUseCase(
            ObjectProvider<ClassroomRepository> classrooms,
            ObjectProvider<SubjectRepository> subjects,
            ObjectProvider<LevelRepository> levels,
            ObjectProvider<UserRepository> users,
            ObjectProvider<CalendarRoomRepository> rooms,
            ObjectProvider<EducationUnitRepository> schools) {
        this.classrooms = classrooms;
        this.subjects = subjects;
        this.levels = levels;
        this.users = users;
        this.rooms = rooms;
        this.schools = schools;
    }

    /** Compatibility constructor for direct unit tests and legacy callers. */
    public CalendarBulkReferenceDataUseCase(
            ClassroomRepository classrooms,
            SubjectRepository subjects,
            LevelRepository levels,
            UserRepository users,
            CalendarRoomRepository rooms,
            EducationUnitRepository schools) {
        this.classrooms = provider(classrooms);
        this.subjects = provider(subjects);
        this.levels = provider(levels);
        this.users = provider(users);
        this.rooms = provider(rooms);
        this.schools = provider(schools);
    }

    public ReferenceData load(String tenantId, String schoolId, CalendarBulkCreateRequest input) {
        ClassroomRepository classroomRepository = require(classrooms, "classroom repository");
        SubjectRepository subjectRepository = require(subjects, "subject repository");
        LevelRepository levelRepository = require(levels, "level repository");
        UserRepository userRepository = require(users, "user repository");
        CalendarRoomRepository roomRepository = require(rooms, "room repository");
        EducationUnitRepository schoolRepository = require(schools, "education-unit repository");

        Map<String, Classroom> classMap = classroomRepository
                .findByTenantIdAndSchoolIdOrderByNameAsc(tenantId, schoolId).stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity()));
        Map<String, Subject> subjectMap = subjectRepository
                .findTop500ByTenantIdAndStatusIgnoreCaseOrderByNameAsc(tenantId, ACTIVE).stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));

        Set<String> subjectIds = input.rows().stream()
                .map(CalendarBulkRowRequest::subjectId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, Level> levelMap = new HashMap<>();
        if (!subjectIds.isEmpty()) {
            levelMap = levelRepository
                    .findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                            tenantId, subjectIds, ACTIVE)
                    .stream().collect(Collectors.toMap(Level::getId, Function.identity()));
        }

        Set<String> userIds = new HashSet<>();
        for (CalendarBulkRowRequest row : input.rows()) {
            if (row.mainTeacherIds() != null) userIds.addAll(row.mainTeacherIds());
            if (row.assistantTeacherIds() != null) userIds.addAll(row.assistantTeacherIds());
        }
        Map<String, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMap = userRepository.findByTenantIdAndIdIn(tenantId, userIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
        }

        Set<String> requestedRoomIds = input.rows().stream()
                .map(CalendarBulkRowRequest::roomId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (!requestedRoomIds.isEmpty()) {
            Set<String> availableRoomIds = roomRepository
                    .findByTenantIdAndSchoolIdAndStatus(tenantId, schoolId, ACTIVE).stream()
                    .map(CalendarRoom::getId).collect(Collectors.toSet());
            Set<String> unavailableRoomIds = new LinkedHashSet<>(requestedRoomIds);
            unavailableRoomIds.removeAll(availableRoomIds);
            if (!unavailableRoomIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Room is not active in the selected tenant/school: " + unavailableRoomIds);
            }
        }

        Map<String, EducationUnit> schoolMap = new HashMap<>();
        if (schoolId != null && !schoolId.isBlank()) {
            schoolMap = schoolRepository.findByIdInOrderByNameAsc(List.of(schoolId)).stream()
                    .collect(Collectors.toMap(EducationUnit::getId, Function.identity()));
        }
        return new ReferenceData(classMap, subjectMap, levelMap, userMap, schoolMap);
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public T getObject() { return value; }
        };
    }

    private static <T> T require(ObjectProvider<T> provider, String label) {
        T value = provider == null ? null : provider.getIfAvailable();
        if (value == null) throw new IllegalStateException("Calendar " + label + " is not available");
        return value;
    }
}
