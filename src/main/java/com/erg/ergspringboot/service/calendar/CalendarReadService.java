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
import com.erg.ergspringboot.repository.calendar.projection.CalendarTeacherColorProjection;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.exception.BadRequestException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class CalendarReadService {

    private static final Logger log = LoggerFactory.getLogger(CalendarReadService.class);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<String> DEFAULT_STATUSES = List.of("DRAFT", "PUBLISHED");
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String FILTER_SENTINEL = "__none__";
    private static final int MAX_WORKSPACE_DAYS = 31;
    private static final int MAX_FILTER_VALUES = 100;
    private static final int MAX_CATALOG_OPTIONS = 500;

    private static final List<String> COLOR_KEYS = List.of(
            "blue", "green", "orange", "purple", "red", "teal", "pink", "indigo"
    );

    private final CalendarEventRepository eventRepository;
    private final CalendarEventTeacherRepository eventTeacherRepository;
    private final ClassroomRepository classroomRepository;
    private final SubjectRepository subjectRepository;
    private final LevelRepository levelRepository;
    private final EducationUnitRepository educationUnitRepository;
    private final CalendarRoomRepository roomRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final CalendarBatchRepository batchRepository;
    private final ErgReadTaskExecutor readTaskExecutor;
    private final CalendarCatalogQueryUseCase catalogQueries;
    private final CalendarScheduleBatchQueryUseCase scheduleBatchQueries;
    private final CalendarPermissionsQueryUseCase permissionQueries;
    private final CalendarEventResponseAssembler eventResponses;
    private final CalendarWorkspaceAssembler workspaceAssembler;
    private final CalendarWorkspaceQueryUseCase workspaceQueries;
    private final CalendarReferenceMapsQueryUseCase referenceQueries;
    private final CalendarEventDetailQueryUseCase eventDetails;

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
        this.eventRepository = eventRepository;
        this.eventTeacherRepository = eventTeacherRepository;
        this.classroomRepository = classroomRepository;
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.educationUnitRepository = educationUnitRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.readTaskExecutor = readTaskExecutor;
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
        // JDBC/JPA must remain on the transaction-bound request thread so the EntityManager
        // snapshot and transaction-local tenant/RLS GUCs are preserved.
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
        Map<String, String> teacherColorKeys = findTeacherColorKeysForCatalog(tenantId, schoolIds, from, to);
        List<User> scopedTeachers = findTeachersForCatalog(tenantId, schoolIds);
        List<CalendarRoomOptionResponse> roomOptions = schoolIds == null || schoolIds.isEmpty() ? List.of()
                : nonNull(roomRepository.findByTenantIdAndSchoolIdInAndStatus(
                        tenantId, schoolIds, STATUS_ACTIVE, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                        () -> roomRepository.findByTenantIdAndSchoolIdInAndStatus(tenantId, schoolIds, STATUS_ACTIVE)).stream()
                        .map(r -> new CalendarRoomOptionResponse(r.getId(), r.getName(), r.getSchoolId())).toList();
        requireCatalogBound(schools, "schools");
        requireCatalogBound(classes, "classes");
        requireCatalogBound(subjects, "subjects");
        requireCatalogBound(scopedTeachers, "teachers");
        requireCatalogBound(roomOptions, "rooms");

        Map<String, EducationUnit> schoolMap = schools.stream()
                .collect(Collectors.toMap(EducationUnit::getId, Function.identity()));

        List<CalendarFilterOptionResponse> schoolOptions = schools.stream()
                .map(s -> new CalendarFilterOptionResponse(s.getId(), s.getName(), 0, null)).toList();

        List<CalendarClassOptionResponse> classOptions = classes.stream()
                .map(c -> new CalendarClassOptionResponse(c.getId(), c.getSchoolId(), c.getName(), c.getGrade(), c.getAcademicYear())).toList();

        List<CalendarSubjectOptionResponse> subjectOptions = subjects.stream()
                .map(s -> new CalendarSubjectOptionResponse(s.getId(), s.getName())).toList();

        Set<String> subjectIds = subjects.stream().map(Subject::getId).collect(Collectors.toSet());
        List<CalendarLevelOptionResponse> levelOptions = subjectIds.isEmpty() ? List.of()
                : nonNull(levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                        tenantId, subjectIds, STATUS_ACTIVE, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                        () -> levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                                tenantId, subjectIds, STATUS_ACTIVE)).stream()
                        .map(l -> new CalendarLevelOptionResponse(l.getId(), l.getSubjectId(), l.getName(), l.getOrder()))
                        .toList();
        requireCatalogBound(levelOptions, "levels");

        Set<String> scopedTeacherIds = scopedTeachers.stream().map(User::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> teacherIdsFromClassesAndEvents = new LinkedHashSet<>();
        teacherIdsFromClassesAndEvents.addAll(findHomeroomTeacherIdsForCatalog(tenantId, schoolIds));
        teacherIdsFromClassesAndEvents.addAll(teacherColorKeys.keySet());
        List<String> assignmentOnlyTeacherIds = teacherIdsFromClassesAndEvents.stream()
                .filter(id -> !scopedTeacherIds.contains(id))
                .toList();
        requireCatalogBound(assignmentOnlyTeacherIds, "teachers");
        List<User> assignmentOnlyTeachers = assignmentOnlyTeacherIds.isEmpty() ? List.of()
                : userRepository.findAllById(assignmentOnlyTeacherIds);
        List<User> teachers = new ArrayList<>(scopedTeachers);
        teachers.addAll(assignmentOnlyTeachers);
        requireCatalogBound(teachers, "teachers");

        List<CalendarTeacherOptionResponse> teacherOptions = teachers.stream()
                .map(t -> new CalendarTeacherOptionResponse(t.getId(), t.getFullName(), teacherColorKeys.get(t.getId()))).toList();

        return new CalendarCatalogResponse(schoolOptions, classOptions, teacherOptions, teacherOptions,
                subjectOptions, levelOptions, roomOptions);
    }

    private void requireCatalogBound(Collection<?> options, String section) {
        if (options != null && options.size() > MAX_CATALOG_OPTIONS) {
            throw new BadRequestException("calendar catalog " + section + " exceeds "
                    + MAX_CATALOG_OPTIONS + " rows; select fewer schools or use catalog search");
        }
    }

    private <T> List<T> nonNull(List<T> primary, java.util.function.Supplier<List<T>> fallback) {
        return primary == null ? fallback.get() : primary;
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

    private List<User> findTeachersForCatalog(String tenantId, List<String> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return sortUsersForCatalog(nonNull(userRepository.findCalendarTeachersByTenant(
                    tenantId, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                    () -> userRepository.findCalendarTeachersByTenant(tenantId)));
        }
        return sortUsersForCatalog(nonNull(userRepository.findCalendarTeachersByTenantAndSchoolIds(
                tenantId, schoolIds, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                () -> userRepository.findCalendarTeachersByTenantAndSchoolIds(tenantId, schoolIds)));
    }

    private List<String> findHomeroomTeacherIdsForCatalog(String tenantId, List<String> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return nonNull(classroomRepository.findHomeroomTeacherIdsByTenant(
                    tenantId, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                    () -> classroomRepository.findHomeroomTeacherIdsByTenant(tenantId));
        }
        return nonNull(classroomRepository.findHomeroomTeacherIdsByTenantAndSchoolIds(
                tenantId, schoolIds, PageRequest.of(0, MAX_CATALOG_OPTIONS + 1)),
                () -> classroomRepository.findHomeroomTeacherIdsByTenantAndSchoolIds(tenantId, schoolIds));
    }

    private Map<String, String> findTeacherColorKeysForCatalog(
            String tenantId,
            List<String> schoolIds,
            OffsetDateTime from,
            OffsetDateTime to) {
        if (schoolIds == null || schoolIds.isEmpty()) return Map.of();
        Map<String, String> teacherColorKeys = new LinkedHashMap<>();
        for (CalendarTeacherColorProjection assignment : eventTeacherRepository.findTeacherColorKeysInRange(
                tenantId, schoolIds, from, to, DEFAULT_STATUSES)) {
            if (assignment.teacherUserId() == null) continue;
            teacherColorKeys.putIfAbsent(assignment.teacherUserId(), assignment.colorKey());
        }
        return teacherColorKeys;
    }

    private List<User> sortUsersForCatalog(List<User> users) {
        if (users == null || users.isEmpty()) return List.of();
        return users.stream()
                .filter(user -> user != null && user.getId() != null)
                .sorted(Comparator.comparing(this::teacherSortLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String teacherSortLabel(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        if (user.getEmail() != null && !user.getEmail().isBlank()) return user.getEmail();
        return user.getId();
    }

}
