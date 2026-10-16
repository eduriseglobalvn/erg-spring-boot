package com.erg.ergspringboot.service.calendar;

import com.erg.ergspringboot.calendar.CalendarReadCompatibility;
import com.erg.ergspringboot.calendar.CalendarReadCompatibilityPort;
import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.CalendarReferenceMaps;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.*;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.platform.concurrency.ErgReadTaskExecutor;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.repository.calendar.CalendarRoomRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @deprecated Use {@link CalendarReadPort} through {@code CalendarOperations};
 *             this bean is a compatibility adapter and owns no read logic.
 */
@Deprecated
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class CalendarReadService implements CalendarReadPort {
    private final CalendarReadPort operations;
    private final CalendarReadCompatibilityPort compatibility;

    @Autowired
    public CalendarReadService(CalendarReadPort operations) {
        this.operations = operations;
        this.compatibility = operations instanceof CalendarReadCompatibilityPort candidate
                ? candidate
                : null;
    }

    /** Compatibility constructor retained while legacy tests/callers are migrated. */
    @Deprecated
    public CalendarReadService(
            CalendarEventRepository eventRepository,
            CalendarEventTeacherRepository eventTeacherRepository,
            ClassroomRepository classroomRepository,
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            EducationUnitRepository educationUnitRepository,
            CalendarRoomRepository roomRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            CalendarBatchRepository batchRepository,
            ErgReadTaskExecutor readTaskExecutor) {
        var useCase = CalendarReadCompatibility.fromRepositories(
                eventRepository, eventTeacherRepository, classroomRepository, subjectRepository,
                levelRepository, educationUnitRepository, roomRepository, userRepository,
                studentRepository, batchRepository, readTaskExecutor);
        this.compatibility = useCase;
        this.operations = useCase;
    }

    @Override
    public CalendarEventResponse eventDetail(String tenantId, String eventId) {
        return operations.eventDetail(tenantId, eventId);
    }

    @Override
    public List<CalendarRoomOptionResponse> activeRooms(String tenantId, String schoolId) {
        return operations.activeRooms(tenantId, schoolId);
    }

    @Override
    @Cacheable(cacheNames = "calendarWorkspace",
            key = "{#tenantId, #schoolIds, #from, #to, #viewMode, #teacherIds, #classIds, #subjectIds, #levelIds, #statuses, #principal.userId(), #principal.permissions(), #principal.deniedPermissions()}")
    public CalendarWorkspaceResponse lcmsTeachingCalendarWorkspace(
            String tenantId, List<String> schoolIds, String from, String to, String viewMode,
            List<String> teacherIds, List<String> classIds, List<String> subjectIds,
            List<String> levelIds, List<String> statuses, ErgPrincipal principal) {
        return operations.lcmsTeachingCalendarWorkspace(tenantId, schoolIds, from, to, viewMode,
                teacherIds, classIds, subjectIds, levelIds, statuses, principal);
    }

    @Override
    @Cacheable(cacheNames = "calendarWorkspace",
            key = "{#tenantId, #teacherUserId, #from, #to, #viewMode, #schoolIds, #classIds, #subjectIds, #levelIds, #statuses, #principal.userId(), #principal.permissions(), #principal.deniedPermissions()}")
    public CalendarWorkspaceResponse teacherTeachingCalendarWorkspace(
            String tenantId, String teacherUserId, String from, String to, String viewMode,
            List<String> schoolIds, List<String> classIds, List<String> subjectIds,
            List<String> levelIds, List<String> statuses, ErgPrincipal principal) {
        return operations.teacherTeachingCalendarWorkspace(tenantId, teacherUserId, from, to, viewMode,
                schoolIds, classIds, subjectIds, levelIds, statuses, principal);
    }

    @Override
    @Cacheable(cacheNames = "calendarWorkspace",
            key = "{#tenantId, #studentUserId, #from, #to, #viewMode, #principal.userId(), #principal.permissions(), #principal.deniedPermissions()}")
    public CalendarWorkspaceResponse studentTeachingCalendarWorkspace(
            String tenantId, String studentUserId, String from, String to, String viewMode,
            List<String> subjectIds, List<String> levelIds, List<String> statuses,
            ErgPrincipal principal) {
        return operations.studentTeachingCalendarWorkspace(tenantId, studentUserId, from, to, viewMode,
                subjectIds, levelIds, statuses, principal);
    }

    @Override
    public CalendarBatchPageResponse teachingScheduleBatches(
            String tenantId, String schoolId, String status, Integer page, Integer size) {
        return operations.teachingScheduleBatches(tenantId, schoolId, status, page, size);
    }

    @Override
    public CalendarBatchDetailResponse teachingScheduleBatch(String tenantId, String batchId) {
        return operations.teachingScheduleBatch(tenantId, batchId);
    }

    @Deprecated
    public CalendarEventResponse buildEventResponse(
            CalendarEvent event, List<CalendarEventTeacher> teachers, CalendarReferenceMaps references) {
        return requireCompatibility().buildEventResponse(event, teachers, references);
    }

    // Package-private overloads remain only for the legacy focused test suite.
    CalendarEventResponse buildEventResponse(
            CalendarEvent event, List<CalendarEventTeacher> teachers, ReferenceMaps references) {
        return buildEventResponse(event, teachers, toModuleReferences(references));
    }

    @Deprecated
    public CalendarFilterOptionsResponse buildFilterOptions(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            CalendarReferenceMaps references,
            String viewMode) {
        return requireCompatibility().buildFilterOptions(events, assignmentsByEvent, references, viewMode);
    }

    CalendarFilterOptionsResponse buildFilterOptions(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            ReferenceMaps references,
            String viewMode) {
        return buildFilterOptions(events, assignmentsByEvent, toModuleReferences(references), viewMode);
    }

    @Deprecated
    public List<CalendarLaneSummaryResponse> buildLaneSummaries(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            CalendarReferenceMaps references,
            String viewMode) {
        return requireCompatibility().buildLaneSummaries(events, assignmentsByEvent, references, viewMode);
    }

    List<CalendarLaneSummaryResponse> buildLaneSummaries(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            ReferenceMaps references,
            String viewMode) {
        return buildLaneSummaries(events, assignmentsByEvent, toModuleReferences(references), viewMode);
    }

    @Deprecated
    public CalendarCatalogResponse buildCatalog(
            String tenantId, List<String> schoolIds, OffsetDateTime from, OffsetDateTime to) {
        return requireCompatibility().buildCatalog(tenantId, schoolIds, from, to);
    }

    @Deprecated
    public CalendarPermissionsResponse buildPermissions(ErgPrincipal principal) {
        return requireCompatibility().buildPermissions(principal);
    }

    /** Legacy test-only shape retained until the old service package is removed. */
    @Deprecated
    public static record ReferenceMaps(
            Map<String, com.erg.ergspringboot.model.organization.EducationUnit> schoolMap,
            Map<String, com.erg.ergspringboot.model.lms.Classroom> classMap,
            Map<String, com.erg.ergspringboot.model.lms.Subject> subjectMap,
            Map<String, com.erg.ergspringboot.model.lms.Level> levelMap,
            Map<String, com.erg.ergspringboot.model.users.User> userMap,
            Map<String, String> teacherColorKeyMap
    ) { }

    private CalendarReferenceMaps toModuleReferences(ReferenceMaps references) {
        if (references == null) return null;
        return new CalendarReferenceMaps(
                references.schoolMap(), references.classMap(), references.subjectMap(),
                references.levelMap(), references.userMap(), references.teacherColorKeyMap());
    }

    private CalendarReadCompatibilityPort requireCompatibility() {
        if (compatibility == null) {
            throw new IllegalStateException("Calendar compatibility projection is unavailable on the canonical port");
        }
        return compatibility;
    }
}
