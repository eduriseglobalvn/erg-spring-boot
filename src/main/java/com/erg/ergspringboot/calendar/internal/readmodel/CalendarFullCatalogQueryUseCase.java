package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarCatalogResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarClassOptionResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarFilterOptionResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarLevelOptionResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOptionResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarSubjectOptionResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarTeacherOptionResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.repository.calendar.CalendarRoomRepository;
import com.erg.ergspringboot.repository.calendar.projection.CalendarTeacherColorProjection;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;

/** Owns full catalog composition while the compatibility facade keeps caching and transactions. */
public final class CalendarFullCatalogQueryUseCase {

    private static final List<String> DEFAULT_STATUSES = List.of("DRAFT", "PUBLISHED");
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int MAX_CATALOG_OPTIONS = 500;

    private final CalendarEventTeacherRepository eventTeacherRepository;
    private final ClassroomRepository classroomRepository;
    private final SubjectRepository subjectRepository;
    private final LevelRepository levelRepository;
    private final EducationUnitRepository educationUnitRepository;
    private final CalendarRoomRepository roomRepository;
    private final UserRepository userRepository;

    public CalendarFullCatalogQueryUseCase(
            CalendarEventTeacherRepository eventTeacherRepository,
            ClassroomRepository classroomRepository,
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            EducationUnitRepository educationUnitRepository,
            CalendarRoomRepository roomRepository,
            UserRepository userRepository) {
        this.eventTeacherRepository = eventTeacherRepository;
        this.classroomRepository = classroomRepository;
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.educationUnitRepository = educationUnitRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    public CalendarCatalogResponse catalog(
            String tenantId,
            List<String> schoolIds,
            OffsetDateTime from,
            OffsetDateTime to) {
        // Keep repository access on the transaction-bound request thread. The facade owns
        // the read-only transaction and invokes this object synchronously.
        List<EducationUnit> schools = schoolIds != null && !schoolIds.isEmpty()
                ? educationUnitRepository.findByIdInOrderByNameAsc(schoolIds) : List.of();
        List<Classroom> classes = schoolIds != null && !schoolIds.isEmpty()
                ? nonNull(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(
                        tenantId, schoolIds, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                        () -> classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(tenantId, schoolIds))
                : List.of();
        List<Subject> subjects = nonNull(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(
                        tenantId, STATUS_ACTIVE, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                () -> subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(tenantId, STATUS_ACTIVE));
        Map<String, String> teacherColorKeys = findTeacherColorKeys(tenantId, schoolIds, from, to);
        List<User> scopedTeachers = findTeachers(tenantId, schoolIds);
        List<CalendarRoomOptionResponse> roomOptions = schoolIds == null || schoolIds.isEmpty() ? List.of()
                : nonNull(roomRepository.findByTenantIdAndSchoolIdInAndStatus(
                        tenantId, schoolIds, STATUS_ACTIVE, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                        () -> roomRepository.findByTenantIdAndSchoolIdInAndStatus(
                                tenantId, schoolIds, STATUS_ACTIVE)).stream()
                        .map(room -> new CalendarRoomOptionResponse(
                                room.getId(), room.getName(), room.getSchoolId()))
                        .toList();
        requireBound(schools, "schools");
        requireBound(classes, "classes");
        requireBound(subjects, "subjects");
        requireBound(scopedTeachers, "teachers");
        requireBound(roomOptions, "rooms");

        Map<String, EducationUnit> schoolMap = schools.stream()
                .collect(Collectors.toMap(EducationUnit::getId, Function.identity()));

        List<CalendarFilterOptionResponse> schoolOptions = schools.stream()
                .map(school -> new CalendarFilterOptionResponse(
                        school.getId(), school.getName(), 0, null))
                .toList();
        List<CalendarClassOptionResponse> classOptions = classes.stream()
                .map(classroom -> new CalendarClassOptionResponse(
                        classroom.getId(), classroom.getSchoolId(), classroom.getName(),
                        classroom.getGrade(), classroom.getAcademicYear()))
                .toList();
        List<CalendarSubjectOptionResponse> subjectOptions = subjects.stream()
                .map(subject -> new CalendarSubjectOptionResponse(subject.getId(), subject.getName()))
                .toList();

        Set<String> subjectIds = subjects.stream().map(Subject::getId).collect(Collectors.toSet());
        List<CalendarLevelOptionResponse> levelOptions = subjectIds.isEmpty() ? List.of()
                : nonNull(levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                        tenantId, subjectIds, STATUS_ACTIVE, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                        () -> levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                                tenantId, subjectIds, STATUS_ACTIVE)).stream()
                        .map(level -> new CalendarLevelOptionResponse(
                                level.getId(), level.getSubjectId(), level.getName(), level.getOrder()))
                        .toList();
        requireBound(levelOptions, "levels");

        Set<String> scopedTeacherIds = scopedTeachers.stream()
                .map(User::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> referencedTeacherIds = new LinkedHashSet<>();
        referencedTeacherIds.addAll(findHomeroomTeacherIds(tenantId, schoolIds));
        referencedTeacherIds.addAll(teacherColorKeys.keySet());
        List<String> assignmentOnlyTeacherIds = referencedTeacherIds.stream()
                .filter(id -> !scopedTeacherIds.contains(id))
                .toList();
        requireBound(assignmentOnlyTeacherIds, "teachers");
        List<User> assignmentOnlyTeachers = assignmentOnlyTeacherIds.isEmpty() ? List.of()
                : userRepository.findAllById(assignmentOnlyTeacherIds);
        List<User> teachers = new ArrayList<>(scopedTeachers);
        teachers.addAll(assignmentOnlyTeachers);
        requireBound(teachers, "teachers");

        List<CalendarTeacherOptionResponse> teacherOptions = teachers.stream()
                .map(teacher -> new CalendarTeacherOptionResponse(
                        teacher.getId(), teacher.getFullName(), teacherColorKeys.get(teacher.getId())))
                .toList();
        return new CalendarCatalogResponse(
                schoolOptions, classOptions, teacherOptions, teacherOptions,
                subjectOptions, levelOptions, roomOptions);
    }

    private List<User> findTeachers(String tenantId, List<String> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return sortUsers(nonNull(userRepository.findCalendarTeachersByTenant(
                    tenantId, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                    () -> userRepository.findCalendarTeachersByTenant(tenantId)));
        }
        return sortUsers(nonNull(userRepository.findCalendarTeachersByTenantAndSchoolIds(
                tenantId, schoolIds, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                () -> userRepository.findCalendarTeachersByTenantAndSchoolIds(tenantId, schoolIds)));
    }

    private List<String> findHomeroomTeacherIds(String tenantId, List<String> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return nonNull(classroomRepository.findHomeroomTeacherIdsByTenant(
                    tenantId, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                    () -> classroomRepository.findHomeroomTeacherIdsByTenant(tenantId));
        }
        return nonNull(classroomRepository.findHomeroomTeacherIdsByTenantAndSchoolIds(
                tenantId, schoolIds, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                () -> classroomRepository.findHomeroomTeacherIdsByTenantAndSchoolIds(tenantId, schoolIds));
    }

    private Map<String, String> findTeacherColorKeys(
            String tenantId,
            List<String> schoolIds,
            OffsetDateTime from,
            OffsetDateTime to) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> teacherColorKeys = new LinkedHashMap<>();
        for (CalendarTeacherColorProjection assignment : eventTeacherRepository.findTeacherColorKeysInRange(
                tenantId, schoolIds, from, to, DEFAULT_STATUSES)) {
            if (assignment.teacherUserId() != null) {
                teacherColorKeys.putIfAbsent(assignment.teacherUserId(), assignment.colorKey());
            }
        }
        return teacherColorKeys;
    }

    private List<User> sortUsers(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return users.stream()
                .filter(user -> user != null && user.getId() != null)
                .sorted(Comparator.comparing(this::teacherSortLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String teacherSortLabel(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return user.getId();
    }

    private void requireBound(Collection<?> options, String section) {
        if (options != null && options.size() > MAX_CATALOG_OPTIONS) {
            throw new BadRequestException("calendar catalog " + section + " exceeds "
                    + MAX_CATALOG_OPTIONS + " rows; select fewer schools or use catalog search");
        }
    }

    private <T> List<T> nonNull(List<T> primary, Supplier<List<T>> fallback) {
        return primary == null ? fallback.get() : primary;
    }
}
