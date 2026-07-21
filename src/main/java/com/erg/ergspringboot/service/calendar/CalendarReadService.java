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
    }

    // -------------------------------------------------------------------------
    // 1. LCMS aggregate teaching calendar workspace
    // -------------------------------------------------------------------------

    public CalendarEventResponse eventDetail(String tenantId, String eventId) {
        CalendarEvent event = eventRepository.findByTenantIdAndId(tenantId, eventId)
                .orElseThrow(() -> new com.erg.ergspringboot.exception.ResourceNotFoundException("Calendar event not found."));
        List<CalendarEventTeacher> teachers = eventTeacherRepository
                .findByTenantIdAndEventIdIn(tenantId, List.of(eventId));
        ReferenceMaps references = buildReferenceMaps(
                tenantId, List.of(event.getSchoolId()), List.of(event), teachers);
        return buildEventResponse(event, teachers, references);
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

        OffsetDateTime fromDateTime = parseDayStart(from);
        OffsetDateTime toDateTime = parseDayAfter(to);
        validateWorkspaceBounds(from, to, schoolIds, teacherIds, classIds, subjectIds, levelIds);
        List<String> effectiveStatuses = statuses != null && !statuses.isEmpty() ? statuses : DEFAULT_STATUSES;

        List<CalendarEvent> filteredEvents = eventRepository.findLcmsWorkspaceEvents(
                tenantId,
                schoolIds,
                toDateTime,
                fromDateTime,
                effectiveStatuses,
                hasValues(teacherIds),
                queryValues(teacherIds),
                hasValues(classIds),
                queryValues(classIds),
                hasValues(subjectIds),
                queryValues(subjectIds),
                hasValues(levelIds),
                queryValues(levelIds));

        List<CalendarEventTeacher> allAssignments = batchLoadTeacherAssignments(tenantId, filteredEvents);
        Map<String, List<CalendarEventTeacher>> assignmentsByEvent = allAssignments.stream()
                .collect(Collectors.groupingBy(CalendarEventTeacher::getEventId));

        ReferenceMaps refMaps = buildReferenceMaps(tenantId, schoolIds, filteredEvents, allAssignments);

        List<CalendarEventResponse> eventResponses = filteredEvents.stream()
                .map(event -> buildEventResponse(event,
                        assignmentsByEvent.getOrDefault(event.getId(), List.of()), refMaps))
                .toList();

        CalendarFilterOptionsResponse filterOptions = buildFilterOptions(
                filteredEvents, assignmentsByEvent, refMaps, viewMode);

        List<CalendarLaneSummaryResponse> laneSummaries = buildLaneSummaries(
                filteredEvents, assignmentsByEvent, refMaps, viewMode);

        CalendarCatalogResponse catalog = buildCatalog(tenantId, schoolIds, fromDateTime, toDateTime);
        CalendarPermissionsResponse permissions = buildPermissions(principal);

        return new CalendarWorkspaceResponse(
                tenantId, from, to, viewMode, eventResponses,
                filterOptions, laneSummaries, catalog, permissions,
                OffsetDateTime.now().toString());
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

        OffsetDateTime fromDateTime = parseDayStart(from);
        OffsetDateTime toDateTime = parseDayAfter(to);
        validateWorkspaceBounds(from, to, null, null, classIds, subjectIds, levelIds);
        List<String> effectiveStatuses = List.of("PUBLISHED");

        List<CalendarEvent> filteredEvents = eventRepository.findTeacherCalendarEventsFiltered(
                tenantId,
                teacherUserId,
                toDateTime,
                fromDateTime,
                effectiveStatuses,
                hasValues(schoolIds),
                queryValues(schoolIds),
                hasValues(classIds),
                queryValues(classIds),
                hasValues(subjectIds),
                queryValues(subjectIds),
                hasValues(levelIds),
                queryValues(levelIds));

        List<CalendarEventTeacher> allAssignments = batchLoadTeacherAssignments(tenantId, filteredEvents);
        Map<String, List<CalendarEventTeacher>> assignmentsByEvent = allAssignments.stream()
                .collect(Collectors.groupingBy(CalendarEventTeacher::getEventId));

        Set<String> effectiveSchoolIds = schoolIds != null && !schoolIds.isEmpty()
                ? new HashSet<>(schoolIds)
                : filteredEvents.stream().map(CalendarEvent::getSchoolId).collect(Collectors.toSet());
        ReferenceMaps refMaps = buildReferenceMaps(
                tenantId, new ArrayList<>(effectiveSchoolIds), filteredEvents, allAssignments);

        List<CalendarEventResponse> eventResponses = filteredEvents.stream()
                .map(event -> buildEventResponse(event,
                        assignmentsByEvent.getOrDefault(event.getId(), List.of()), refMaps))
                .toList();

        CalendarFilterOptionsResponse filterOptions = buildFilterOptions(
                filteredEvents, assignmentsByEvent, refMaps, viewMode);

        List<CalendarLaneSummaryResponse> laneSummaries = buildLaneSummaries(
                filteredEvents, assignmentsByEvent, refMaps, viewMode);

        CalendarCatalogResponse catalog = buildCatalog(tenantId, new ArrayList<>(effectiveSchoolIds), fromDateTime, toDateTime);
        CalendarPermissionsResponse permissions = buildPermissions(principal);

        return new CalendarWorkspaceResponse(
                tenantId, from, to, viewMode, eventResponses,
                filterOptions, laneSummaries, catalog, permissions,
                OffsetDateTime.now().toString());
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

        // Look up student record to determine academicClassId
        Student student = studentRepository
                .findFirstByTenantIdAndAuthUserId(tenantId, studentUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Student record not found for userId: " + studentUserId));

        String academicClassId = student.getAcademicClassId();
        if (academicClassId == null) {
            throw new IllegalArgumentException(
                    "Student " + studentUserId + " has no academic class assignment.");
        }

        OffsetDateTime fromDateTime = parseDayStart(from);
        OffsetDateTime toDateTime = parseDayAfter(to);
        validateWorkspaceBounds(from, to, null, null, null, subjectIds, levelIds);
        List<String> effectiveStatuses = List.of("PUBLISHED");

        List<CalendarEvent> filteredEvents = eventRepository.findStudentCalendarEventsFiltered(
                tenantId,
                academicClassId,
                toDateTime,
                fromDateTime,
                effectiveStatuses,
                hasValues(subjectIds),
                queryValues(subjectIds),
                hasValues(levelIds),
                queryValues(levelIds));

        List<CalendarEventTeacher> allAssignments = batchLoadTeacherAssignments(tenantId, filteredEvents);
        Map<String, List<CalendarEventTeacher>> assignmentsByEvent = allAssignments.stream()
                .collect(Collectors.groupingBy(CalendarEventTeacher::getEventId));

        Set<String> effectiveSchoolIds = filteredEvents.stream()
                .map(CalendarEvent::getSchoolId)
                .collect(Collectors.toSet());
        ReferenceMaps refMaps = buildReferenceMaps(
                tenantId, new ArrayList<>(effectiveSchoolIds), filteredEvents, allAssignments);

        List<CalendarEventResponse> eventResponses = filteredEvents.stream()
                .map(event -> buildEventResponse(event,
                        assignmentsByEvent.getOrDefault(event.getId(), List.of()), refMaps))
                .toList();

        CalendarFilterOptionsResponse filterOptions = buildFilterOptions(
                filteredEvents, assignmentsByEvent, refMaps, viewMode);

        List<CalendarLaneSummaryResponse> laneSummaries = buildLaneSummaries(
                filteredEvents, assignmentsByEvent, refMaps, viewMode);

        CalendarCatalogResponse catalog = buildCatalog(tenantId, new ArrayList<>(effectiveSchoolIds), fromDateTime, toDateTime);
        CalendarPermissionsResponse permissions = buildPermissions(principal);

        return new CalendarWorkspaceResponse(
                tenantId, from, to, viewMode, eventResponses,
                filterOptions, laneSummaries, catalog, permissions,
                OffsetDateTime.now().toString());
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

        Map<String, Long> schoolCounts = events.stream()
                .collect(Collectors.groupingBy(CalendarEvent::getSchoolId, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> classCounts = events.stream()
                .collect(Collectors.groupingBy(CalendarEvent::getAcademicClassId, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> subjectCounts = events.stream()
                .filter(e -> e.getSubjectId() != null)
                .collect(Collectors.groupingBy(CalendarEvent::getSubjectId, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> levelCounts = events.stream()
                .filter(e -> e.getLevelId() != null)
                .collect(Collectors.groupingBy(CalendarEvent::getLevelId, LinkedHashMap::new, Collectors.counting()));

        Set<String> eventIdSet = events.stream()
                .map(CalendarEvent::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Long> teacherCounts = new LinkedHashMap<>();
        for (var entry : assignmentsByEvent.entrySet()) {
            if (!eventIdSet.contains(entry.getKey())) continue;
            for (CalendarEventTeacher t : entry.getValue()) {
                teacherCounts.merge(t.getTeacherUserId(), 1L, Long::sum);
            }
        }

        return new CalendarFilterOptionsResponse(
                schoolCounts.entrySet().stream().map(e ->
                        new CalendarFilterOptionResponse(e.getKey(), resolveSchoolName(e.getKey(), refMaps), e.getValue().intValue(), null)).toList(),
                classCounts.entrySet().stream().map(e ->
                        new CalendarFilterOptionResponse(e.getKey(), resolveClassName(e.getKey(), refMaps), e.getValue().intValue(), null)).toList(),
                teacherCounts.entrySet().stream().map(e ->
                        new CalendarFilterOptionResponse(e.getKey(), resolveTeacherName(e.getKey(), refMaps), e.getValue().intValue(), resolveTeacherColorKey(e.getKey(), refMaps))).toList(),
                subjectCounts.entrySet().stream().map(e ->
                        new CalendarFilterOptionResponse(e.getKey(), resolveSubjectName(e.getKey(), refMaps), e.getValue().intValue(), null)).toList(),
                levelCounts.entrySet().stream().map(e ->
                        new CalendarFilterOptionResponse(e.getKey(), resolveLevelName(e.getKey(), refMaps), e.getValue().intValue(), null)).toList());
    }

    List<CalendarLaneSummaryResponse> buildLaneSummaries(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            ReferenceMaps refMaps,
            String viewMode) {
        if (events == null || events.isEmpty()) return List.of();

        String mode = viewMode != null ? viewMode.toUpperCase() : "BY_SCHOOL";

        return switch (mode) {
            case "CLASS", "BY_CLASS" -> buildLaneSummariesByKey(events, CalendarEvent::getAcademicClassId,
                    id -> resolveClassName(id, refMaps), null);
            case "SUBJECT", "BY_SUBJECT" -> buildLaneSummariesByKey(events, CalendarEvent::getSubjectId,
                    id -> resolveSubjectName(id, refMaps), null);
            case "TEACHER", "BY_TEACHER" -> buildTeacherLaneSummaries(events, assignmentsByEvent, refMaps);
            case "ALL" -> buildLaneSummariesByKey(events, CalendarEvent::getSchoolId,
                    id -> resolveSchoolName(id, refMaps), null);
            default -> buildLaneSummariesByKey(events, CalendarEvent::getSchoolId,
                    id -> resolveSchoolName(id, refMaps), null);
        };
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
        for (CalendarEventTeacher assignment : teacherAssignments == null ? List.<CalendarEventTeacher>of() : teacherAssignments) {
            if (assignment.getTeacherUserId() != null) userIdSet.add(assignment.getTeacherUserId());
        }

        List<String> allSchoolIds = new ArrayList<>(schoolIdSet);
        if (schoolIds != null) {
            for (String sid : schoolIds) {
                if (!schoolIdSet.contains(sid)) allSchoolIds.add(sid);
            }
        }

        return new ReferenceMaps(
                allSchoolIds.isEmpty() ? Map.of() : educationUnitRepository.findByIdInOrderByNameAsc(allSchoolIds).stream()
                        .collect(Collectors.toMap(EducationUnit::getId, Function.identity())),
                classIdSet.isEmpty() ? Map.of() : classroomRepository.findAllById(classIdSet).stream()
                        .collect(Collectors.toMap(Classroom::getId, Function.identity())),
                subjectIdSet.isEmpty() ? Map.of() : subjectRepository.findAllById(subjectIdSet).stream()
                        .collect(Collectors.toMap(Subject::getId, Function.identity())),
                levelIdSet.isEmpty() ? Map.of() : levelRepository.findAllById(levelIdSet).stream()
                        .collect(Collectors.toMap(Level::getId, Function.identity())),
                userIdSet.isEmpty() ? Map.of() : userRepository.findAllById(userIdSet).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity())),
                Map.of());
    }

    private List<CalendarEventTeacher> batchLoadTeacherAssignments(String tenantId, List<CalendarEvent> events) {
        if (events == null || events.isEmpty()) return List.of();
        List<String> eventIds = events.stream().map(CalendarEvent::getId).filter(Objects::nonNull).toList();
        if (eventIds.isEmpty()) return List.of();
        return eventTeacherRepository.findByTenantIdAndEventIdIn(tenantId, eventIds);
    }

    private List<CalendarEvent> applyFilters(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            List<String> teacherIds, List<String> classIds,
            List<String> subjectIds, List<String> levelIds) {

        List<CalendarEvent> result = events;
        if (teacherIds != null && !teacherIds.isEmpty()) {
            Set<String> teacherIdSet = new HashSet<>(teacherIds);
            result = result.stream()
                    .filter(event -> assignmentsByEvent.getOrDefault(event.getId(), List.of()).stream()
                            .anyMatch(t -> teacherIdSet.contains(t.getTeacherUserId())))
                    .toList();
        }
        if (classIds != null && !classIds.isEmpty()) {
            Set<String> classIdSet = new HashSet<>(classIds);
            result = result.stream().filter(e -> classIdSet.contains(e.getAcademicClassId())).toList();
        }
        if (subjectIds != null && !subjectIds.isEmpty()) {
            Set<String> subjectIdSet = new HashSet<>(subjectIds);
            result = result.stream().filter(e -> subjectIdSet.contains(e.getSubjectId())).toList();
        }
        if (levelIds != null && !levelIds.isEmpty()) {
            Set<String> levelIdSet = new HashSet<>(levelIds);
            result = result.stream().filter(e -> levelIdSet.contains(e.getLevelId())).toList();
        }
        return result;
    }

    private List<CalendarLaneSummaryResponse> buildLaneSummariesByKey(
            List<CalendarEvent> events,
            java.util.function.Function<CalendarEvent, String> keyExtractor,
            java.util.function.Function<String, String> labelResolver,
            java.util.function.Function<String, String> colorKeyResolver) {
        Map<String, Long> counts = events.stream()
                .filter(e -> keyExtractor.apply(e) != null)
                .collect(Collectors.groupingBy(e -> keyExtractor.apply(e), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> {
                    String id = entry.getKey();
                    String label = labelResolver.apply(id);
                    String colorKey = colorKeyResolver != null ? colorKeyResolver.apply(id) : null;
                    return new CalendarLaneSummaryResponse(id, label != null ? label : id, entry.getValue().intValue(), colorKey);
                })
                .toList();
    }

    private List<CalendarLaneSummaryResponse> buildTeacherLaneSummaries(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            ReferenceMaps refMaps) {
        Map<String, Long> teacherCounts = new LinkedHashMap<>();
        for (CalendarEvent event : events) {
            for (CalendarEventTeacher t : assignmentsByEvent.getOrDefault(event.getId(), List.of())) {
                teacherCounts.merge(t.getTeacherUserId(), 1L, Long::sum);
            }
        }
        return teacherCounts.entrySet().stream()
                .map(entry -> {
                    String id = entry.getKey();
                    String label = resolveTeacherName(id, refMaps);
                    String colorKey = resolveTeacherColorKey(id, refMaps);
                    return new CalendarLaneSummaryResponse(id, label != null ? label : id, entry.getValue().intValue(), colorKey);
                })
                .toList();
    }

    private CalendarColorGroupResponse computeColorGroup(String subjectId) {
        if (subjectId == null) return new CalendarColorGroupResponse("default", null, "subject");
        int index = Math.abs(subjectId.hashCode()) % COLOR_KEYS.size();
        return new CalendarColorGroupResponse(COLOR_KEYS.get(index), null, "subject");
    }

    private CalendarBatchSummaryResponse toBatchSummary(CalendarBatch batch) {
        return new CalendarBatchSummaryResponse(
                batch.getId(),
                batch.getTenantId(),
                batch.getSchoolId(),
                batch.getAcademicYear(),
                batch.getApplyFrom() != null ? batch.getApplyFrom().toString() : null,
                batch.getRepeatWeeks(),
                batch.getStatus(),
                batch.getSource(),
                batch.getNote(),
                batch.getCreatedBy(),
                batch.getPublishedBy(),
                batch.getPublishedAt() != null ? batch.getPublishedAt().toString() : null,
                batch.getCreatedAt() != null ? batch.getCreatedAt().toString() : null);
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

    private static boolean hasValues(List<String> values) {
        return values != null && values.stream().anyMatch(value -> value != null && !value.isBlank());
    }

    private static List<String> queryValues(List<String> values) {
        if (!hasValues(values)) return List.of(FILTER_SENTINEL);
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    private static OffsetDateTime parseDayStart(String date) {
        return LocalDate.parse(date).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
    }

    private static OffsetDateTime parseDayAfter(String date) {
        return LocalDate.parse(date).plusDays(1).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
    }

    private static void validateWorkspaceBounds(
            String from,
            String to,
            List<String> schoolIds,
            List<String> teacherIds,
            List<String> classIds,
            List<String> subjectIds,
            List<String> levelIds
    ) {
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(from);
            end = LocalDate.parse(to);
        } catch (RuntimeException ex) {
            throw new BadRequestException("calendar from/to must be ISO dates");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days < 1 || days > MAX_WORKSPACE_DAYS) {
            throw new BadRequestException("calendar date range must be between 1 and " + MAX_WORKSPACE_DAYS + " days");
        }
        // Arrays.asList intentionally permits null optional filter lists; List.of rejects
        // null elements before the validation branch can treat them as absent.
        for (List<String> values : Arrays.asList(schoolIds, teacherIds, classIds, subjectIds, levelIds)) {
            if (values != null && values.stream().filter(value -> value != null && !value.isBlank()).distinct().count() > MAX_FILTER_VALUES) {
                throw new BadRequestException("calendar filter list exceeds " + MAX_FILTER_VALUES + " values");
            }
        }
    }

    // ---- Name resolution helpers ----

    private String resolveSchoolName(String schoolId, ReferenceMaps refMaps) {
        if (schoolId == null) return null;
        EducationUnit unit = refMaps.schoolMap().get(schoolId);
        return unit != null ? unit.getName() : null;
    }

    private String resolveClassName(String classId, ReferenceMaps refMaps) {
        if (classId == null) return null;
        Classroom classroom = refMaps.classMap().get(classId);
        return classroom != null ? classroom.getName() : null;
    }

    private String resolveSubjectName(String subjectId, ReferenceMaps refMaps) {
        if (subjectId == null) return null;
        Subject subject = refMaps.subjectMap().get(subjectId);
        return subject != null ? subject.getName() : null;
    }

    private String resolveLevelName(String levelId, ReferenceMaps refMaps) {
        if (levelId == null) return null;
        Level level = refMaps.levelMap().get(levelId);
        return level != null ? level.getName() : null;
    }

    private String resolveTeacherName(String teacherUserId, ReferenceMaps refMaps) {
        if (teacherUserId == null) return null;
        User user = refMaps.userMap().get(teacherUserId);
        return user != null ? user.getFullName() : null;
    }

    private String resolveTeacherColorKey(String teacherUserId, ReferenceMaps refMaps) {
        if (teacherUserId == null) return null;
        return refMaps.teacherColorKeyMap().get(teacherUserId);
    }
}
