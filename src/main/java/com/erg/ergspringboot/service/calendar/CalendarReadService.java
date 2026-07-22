package com.erg.ergspringboot.service.calendar;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.*;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.model.calendar.CalendarRoom;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.concurrency.ErgReadTaskExecutor;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.repository.calendar.CalendarRoomRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarCatalogQueryUseCase;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarScheduleBatchQueryUseCase;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarPermissionsQueryUseCase;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarEventResponseAssembler;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarWorkspaceAssembler;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarWorkspaceQueryUseCase;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarReferenceMapsQueryUseCase;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarEventDetailQueryUseCase;
import com.erg.ergspringboot.calendar.internal.readmodel.CalendarFullCatalogQueryUseCase;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class CalendarReadService {

    private final CalendarCatalogQueryUseCase catalogQueries;
    private final CalendarScheduleBatchQueryUseCase scheduleBatchQueries;
    private final CalendarPermissionsQueryUseCase permissionQueries;
    private final CalendarEventResponseAssembler eventResponses;
    private final CalendarWorkspaceAssembler workspaceAssembler;
    private final CalendarWorkspaceQueryUseCase workspaceQueries;
    private final CalendarReferenceMapsQueryUseCase referenceQueries;
    private final CalendarEventDetailQueryUseCase eventDetails;
    private final CalendarFullCatalogQueryUseCase fullCatalogQueries;

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
        this.catalogQueries = new CalendarCatalogQueryUseCase(roomRepository);
        this.scheduleBatchQueries = new CalendarScheduleBatchQueryUseCase(batchRepository, eventRepository);
        this.permissionQueries = new CalendarPermissionsQueryUseCase();
        this.eventResponses = new CalendarEventResponseAssembler();
        this.workspaceAssembler = new CalendarWorkspaceAssembler();
        this.referenceQueries = new CalendarReferenceMapsQueryUseCase(
                educationUnitRepository, classroomRepository, subjectRepository,
                levelRepository, userRepository);
        this.eventDetails = new CalendarEventDetailQueryUseCase(
                eventRepository, eventTeacherRepository, referenceQueries, eventResponses);
        this.fullCatalogQueries = new CalendarFullCatalogQueryUseCase(
                eventTeacherRepository, classroomRepository, subjectRepository, levelRepository,
                educationUnitRepository, roomRepository, userRepository);
        this.workspaceQueries = new CalendarWorkspaceQueryUseCase(
                eventRepository, eventTeacherRepository, studentRepository,
                this::buildReferenceMaps,
                this::buildCatalog,
                this::buildEventResponse,
                this::buildFilterOptions,
                this::buildLaneSummaries,
                this::buildPermissions);
    }

    // -------------------------------------------------------------------------
    // 1. LCMS aggregate teaching calendar workspace
    // -------------------------------------------------------------------------

    public CalendarEventResponse eventDetail(String tenantId, String eventId) {
        return eventDetails.detail(tenantId, eventId);
    }

    public List<CalendarRoomOptionResponse> activeRooms(String tenantId, String schoolId) {
        return catalogQueries.activeRooms(tenantId, schoolId);
    }

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

    // -------------------------------------------------------------------------
    // 2. Teacher personal teaching calendar workspace
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // 3. Student personal teaching calendar workspace
    // -------------------------------------------------------------------------

    @Cacheable(cacheNames = "calendarWorkspace",
            key = "{#tenantId, #studentUserId, #from, #to, #viewMode, #schoolIds, #statuses, #principal.userId(), #principal.permissions(), #principal.deniedPermissions()}")
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

    // -------------------------------------------------------------------------
    // 4. LCMS teaching schedule batch management
    // -------------------------------------------------------------------------

    public CalendarBatchPageResponse teachingScheduleBatches(
            String tenantId,
            String schoolId,
            String status,
            Integer page,
            Integer size) {
        return scheduleBatchQueries.list(tenantId, schoolId, status, page, size);
    }

    public CalendarBatchDetailResponse teachingScheduleBatch(String tenantId, String batchId) {
        return scheduleBatchQueries.detail(tenantId, batchId);
    }

    // =========================================================================
    // Package-private helper methods for tests
    // =========================================================================

    CalendarEventResponse buildEventResponse(
            CalendarEvent event,
            List<CalendarEventTeacher> teachers,
            ReferenceMaps refMaps) {
        return eventResponses.response(event, teachers, refMaps);
    }

    CalendarFilterOptionsResponse buildFilterOptions(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            ReferenceMaps refMaps,
            String viewMode) {
        return workspaceAssembler.filterOptions(events, assignmentsByEvent, refMaps, viewMode);
    }

    List<CalendarLaneSummaryResponse> buildLaneSummaries(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            ReferenceMaps refMaps,
            String viewMode) {
        return workspaceAssembler.laneSummaries(events, assignmentsByEvent, refMaps, viewMode);
    }

    CalendarCatalogResponse buildCatalog(
            String tenantId,
            List<String> schoolIds,
            OffsetDateTime from,
            OffsetDateTime to) {
        return fullCatalogQueries.catalog(tenantId, schoolIds, from, to);
    }

    CalendarPermissionsResponse buildPermissions(ErgPrincipal principal) {
        return permissionQueries.permissions(principal);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    public static record ReferenceMaps(
            Map<String, EducationUnit> schoolMap,
            Map<String, Classroom> classMap,
            Map<String, Subject> subjectMap,
            Map<String, Level> levelMap,
            Map<String, User> userMap,
            Map<String, String> teacherColorKeyMap
    ) {}

    private ReferenceMaps buildReferenceMaps(
            String tenantId,
            List<String> schoolIds,
            List<CalendarEvent> events,
            List<CalendarEventTeacher> teacherAssignments) {
        return referenceQueries.load(tenantId, schoolIds, events, teacherAssignments);
    }

}
