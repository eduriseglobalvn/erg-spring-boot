package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.CalendarReadCompatibilityPort;
import com.erg.ergspringboot.calendar.CalendarReferenceMaps;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.*;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
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
import com.erg.ergspringboot.users.UserDirectoryCompatibility;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendar read-model application service. It owns orchestration while the
 * public module facade and the deprecated service adapter remain technology
 * independent.
 */
@Primary
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class CalendarReadUseCase implements CalendarReadPort, CalendarReadCompatibilityPort {

    private final CalendarCatalogQueryUseCase catalogQueries;
    private final CalendarScheduleBatchQueryUseCase scheduleBatchQueries;
    private final CalendarPermissionsQueryUseCase permissionQueries;
    private final CalendarEventResponseAssembler eventResponses;
    private final CalendarWorkspaceAssembler workspaceAssembler;
    private final CalendarWorkspaceQueryUseCase workspaceQueries;
    private final CalendarReferenceMapsQueryUseCase referenceQueries;
    private final CalendarEventDetailQueryUseCase eventDetails;
    private final CalendarFullCatalogQueryUseCase fullCatalogQueries;

    @Autowired
    public CalendarReadUseCase(
            CalendarEventRepository eventRepository,
            CalendarEventTeacherRepository eventTeacherRepository,
            ClassroomRepository classroomRepository,
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            EducationUnitRepository educationUnitRepository,
            CalendarRoomRepository roomRepository,
            UserDirectoryPort userDirectory,
            UserRepository legacyUserRepository,
            StudentRepository studentRepository,
            CalendarBatchRepository batchRepository,
            CalendarCatalogQueryUseCase catalogQueries,
            CalendarScheduleBatchQueryUseCase scheduleBatchQueries,
            CalendarPermissionsQueryUseCase permissionQueries,
            CalendarEventResponseAssembler eventResponses,
            CalendarWorkspaceAssembler workspaceAssembler) {
        this(eventRepository, eventTeacherRepository, classroomRepository, subjectRepository,
                levelRepository, educationUnitRepository, roomRepository, userDirectory,
                legacyUserRepository,
                studentRepository, batchRepository, catalogQueries, scheduleBatchQueries,
                permissionQueries, eventResponses, workspaceAssembler, true);
    }

    /** Compatibility constructor retained while legacy tests/callers migrate. */
    @Deprecated
    public CalendarReadUseCase(
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
            ErgReadTaskExecutor ignoredReadTaskExecutor) {
        this(eventRepository, eventTeacherRepository, classroomRepository, subjectRepository,
                levelRepository, educationUnitRepository, roomRepository,
                UserDirectoryCompatibility.fromRepository(userRepository),
                userRepository,
                studentRepository, batchRepository,
                new CalendarCatalogQueryUseCase(roomRepository),
                new CalendarScheduleBatchQueryUseCase(batchRepository, eventRepository),
                new CalendarPermissionsQueryUseCase(),
                new CalendarEventResponseAssembler(),
                new CalendarWorkspaceAssembler(), true);
    }

    private CalendarReadUseCase(
            CalendarEventRepository eventRepository,
            CalendarEventTeacherRepository eventTeacherRepository,
            ClassroomRepository classroomRepository,
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            EducationUnitRepository educationUnitRepository,
            CalendarRoomRepository roomRepository,
            UserDirectoryPort userDirectory,
            UserRepository legacyUserRepository,
            StudentRepository studentRepository,
            CalendarBatchRepository batchRepository,
            CalendarCatalogQueryUseCase catalogQueries,
            CalendarScheduleBatchQueryUseCase scheduleBatchQueries,
            CalendarPermissionsQueryUseCase permissionQueries,
            CalendarEventResponseAssembler eventResponses,
            CalendarWorkspaceAssembler workspaceAssembler,
            boolean ignoredCompatibilityMarker) {
        this.catalogQueries = catalogQueries;
        this.scheduleBatchQueries = scheduleBatchQueries;
        this.permissionQueries = permissionQueries;
        this.eventResponses = eventResponses;
        this.workspaceAssembler = workspaceAssembler;
        this.referenceQueries = new CalendarReferenceMapsQueryUseCase(
                educationUnitRepository, classroomRepository, subjectRepository,
                levelRepository, legacyUserRepository);
        this.eventDetails = new CalendarEventDetailQueryUseCase(
                eventRepository, eventTeacherRepository, referenceQueries, eventResponses);
        this.fullCatalogQueries = new CalendarFullCatalogQueryUseCase(
                eventTeacherRepository, classroomRepository, subjectRepository, levelRepository,
                educationUnitRepository, roomRepository, userDirectory);
        this.workspaceQueries = new CalendarWorkspaceQueryUseCase(
                eventRepository, eventTeacherRepository, studentRepository,
                this::buildReferenceMaps,
                fullCatalogQueries::catalog,
                eventResponses::response,
                workspaceAssembler::filterOptions,
                workspaceAssembler::laneSummaries,
                permissionQueries::permissions);
    }

    @Override
    public CalendarEventResponse eventDetail(String tenantId, String eventId) {
        return eventDetails.detail(tenantId, eventId);
    }

    @Override
    public List<CalendarRoomOptionResponse> activeRooms(String tenantId, String schoolId) {
        return catalogQueries.activeRooms(tenantId, schoolId);
    }

    @Override
    @Cacheable(cacheNames = "calendarWorkspace",
            key = "{#tenantId, #schoolIds, #from, #to, #viewMode, #teacherIds, #classIds, #subjectIds, #levelIds, #statuses, #principal.userId(), #principal.permissions(), #principal.deniedPermissions()}")
    public CalendarWorkspaceResponse lcmsTeachingCalendarWorkspace(
            String tenantId,
            List<String> schoolIds,
            String from,
            String to,
            String viewMode,
            List<String> teacherIds,
            List<String> classIds,
            List<String> subjectIds,
            List<String> levelIds,
            List<String> statuses,
            ErgPrincipal principal) {
        return workspaceQueries.lcms(
                tenantId, schoolIds, from, to, viewMode,
                teacherIds, classIds, subjectIds, levelIds, statuses, principal);
    }

    @Override
    @Cacheable(cacheNames = "calendarWorkspace",
            key = "{#tenantId, #teacherUserId, #from, #to, #viewMode, #schoolIds, #classIds, #subjectIds, #levelIds, #statuses, #principal.userId(), #principal.permissions(), #principal.deniedPermissions()}")
    public CalendarWorkspaceResponse teacherTeachingCalendarWorkspace(
            String tenantId,
            String teacherUserId,
            String from,
            String to,
            String viewMode,
            List<String> schoolIds,
            List<String> classIds,
            List<String> subjectIds,
            List<String> levelIds,
            List<String> statuses,
            ErgPrincipal principal) {
        return workspaceQueries.teacher(
                tenantId, teacherUserId, from, to, viewMode,
                schoolIds, classIds, subjectIds, levelIds, statuses, principal);
    }

    @Override
    @Cacheable(cacheNames = "calendarWorkspace",
            key = "{#tenantId, #studentUserId, #from, #to, #viewMode, #principal.userId(), #principal.permissions(), #principal.deniedPermissions()}")
    public CalendarWorkspaceResponse studentTeachingCalendarWorkspace(
            String tenantId,
            String studentUserId,
            String from,
            String to,
            String viewMode,
            List<String> subjectIds,
            List<String> levelIds,
            List<String> statuses,
            ErgPrincipal principal) {
        return workspaceQueries.student(
                tenantId, studentUserId, from, to, viewMode,
                subjectIds, levelIds, statuses, principal);
    }

    @Override
    public CalendarBatchPageResponse teachingScheduleBatches(
            String tenantId, String schoolId, String status, Integer page, Integer size) {
        return scheduleBatchQueries.list(tenantId, schoolId, status, page, size);
    }

    @Override
    public CalendarBatchDetailResponse teachingScheduleBatch(String tenantId, String batchId) {
        return scheduleBatchQueries.detail(tenantId, batchId);
    }

    @Override
    @Deprecated
    public CalendarEventResponse buildEventResponse(
            CalendarEvent event, List<CalendarEventTeacher> teachers, CalendarReferenceMaps references) {
        return eventResponses.response(event, teachers, references);
    }

    @Override
    @Deprecated
    public CalendarFilterOptionsResponse buildFilterOptions(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            CalendarReferenceMaps references,
            String viewMode) {
        return workspaceAssembler.filterOptions(events, assignmentsByEvent, references, viewMode);
    }

    @Override
    @Deprecated
    public List<CalendarLaneSummaryResponse> buildLaneSummaries(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            CalendarReferenceMaps references,
            String viewMode) {
        return workspaceAssembler.laneSummaries(events, assignmentsByEvent, references, viewMode);
    }

    @Override
    @Deprecated
    public CalendarCatalogResponse buildCatalog(
            String tenantId, List<String> schoolIds, OffsetDateTime from, OffsetDateTime to) {
        return fullCatalogQueries.catalog(tenantId, schoolIds, from, to);
    }

    @Override
    @Deprecated
    public CalendarPermissionsResponse buildPermissions(ErgPrincipal principal) {
        return permissionQueries.permissions(principal);
    }

    private CalendarReferenceMaps buildReferenceMaps(
            String tenantId,
            List<String> schoolIds,
            List<CalendarEvent> events,
            List<CalendarEventTeacher> teacherAssignments) {
        return referenceQueries.load(tenantId, schoolIds, events, teacherAssignments);
    }
}
