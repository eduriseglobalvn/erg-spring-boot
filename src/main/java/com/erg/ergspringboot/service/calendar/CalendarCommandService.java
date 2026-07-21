package com.erg.ergspringboot.service.calendar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.calendar.internal.batch.CalendarBatchLifecycleUseCase;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CreateRoomRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBulkPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarColorGroupResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarConflictResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarDeletePayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPreviewPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOption;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRowResultResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarTeacherAssignmentResponse;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.model.calendar.CalendarRoom;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.repository.calendar.CalendarRoomRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.service.calendar.CalendarConflictService.ResolveResult;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService.ExpansionResult;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService.ReferenceData;
import com.erg.ergspringboot.service.calendar.CalendarValidationService.RowValidationResult;
import com.erg.ergspringboot.utils.IdGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class CalendarCommandService {

    private static final Logger log = LoggerFactory.getLogger(CalendarCommandService.class);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final ObjectMapper CANONICAL_OBJECT_MAPPER = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final CalendarBatchRepository batchRepository;
    private final CalendarEventRepository eventRepository;
    private final CalendarEventTeacherRepository eventTeacherRepository;
    private final CalendarRoomRepository roomRepository;
    private final CalendarExpansionService expansionService;
    private final CalendarValidationService validationService;
    private final CalendarBulkInputNormalizer inputNormalizer;
    private final CalendarConflictService conflictService;
    private final GraphQlAccessSupport accessSupport;
    private final ClassroomRepository classroomRepository;
    private final SubjectRepository subjectRepository;
    private final LevelRepository levelRepository;
    private final UserRepository userRepository;
    private final EducationUnitRepository educationUnitRepository;
    private final EntityManager entityManager;
    private final CalendarBatchLifecycleUseCase batchLifecycle;

    /** Compatibility constructor retained for legacy tests and callers. */
    public CalendarCommandService(
            CalendarBatchRepository batchRepository,
            CalendarEventRepository eventRepository,
            CalendarEventTeacherRepository eventTeacherRepository,
            CalendarRoomRepository roomRepository,
            CalendarExpansionService expansionService,
            CalendarValidationService validationService,
            CalendarBulkInputNormalizer inputNormalizer,
            CalendarConflictService conflictService,
            GraphQlAccessSupport accessSupport,
            ClassroomRepository classroomRepository,
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            UserRepository userRepository,
            EducationUnitRepository educationUnitRepository,
            EntityManager entityManager,
            ApplicationEventPublisher eventPublisher) {
        this(batchRepository, eventRepository, eventTeacherRepository, roomRepository,
                expansionService, validationService, inputNormalizer, conflictService,
                accessSupport, classroomRepository, subjectRepository, levelRepository,
                userRepository, educationUnitRepository, entityManager,
                new CalendarBatchLifecycleUseCase(
                        batchRepository, eventRepository, conflictService, eventPublisher));
    }

    @Autowired
    public CalendarCommandService(
            CalendarBatchRepository batchRepository,
            CalendarEventRepository eventRepository,
            CalendarEventTeacherRepository eventTeacherRepository,
            CalendarRoomRepository roomRepository,
            CalendarExpansionService expansionService,
            CalendarValidationService validationService,
            CalendarBulkInputNormalizer inputNormalizer,
            CalendarConflictService conflictService,
            GraphQlAccessSupport accessSupport,
            ClassroomRepository classroomRepository,
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            UserRepository userRepository,
            EducationUnitRepository educationUnitRepository,
            EntityManager entityManager,
            CalendarBatchLifecycleUseCase batchLifecycle) {
        this.batchRepository = batchRepository;
        this.eventRepository = eventRepository;
        this.eventTeacherRepository = eventTeacherRepository;
        this.roomRepository = roomRepository;
        this.expansionService = expansionService;
        this.validationService = validationService;
        this.inputNormalizer = inputNormalizer;
        this.conflictService = conflictService;
        this.accessSupport = accessSupport;
        this.classroomRepository = classroomRepository;
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.userRepository = userRepository;
        this.educationUnitRepository = educationUnitRepository;
        this.entityManager = entityManager;
        this.batchLifecycle = batchLifecycle;
    }

    @Transactional("postgresTransactionManager")
    public CalendarPreviewPayload preview(
            String tenantId, CalendarBulkCreateRequest input, String userId) {
        input = inputNormalizer.normalize(input);
        ReferenceData refData = loadReferenceData(tenantId, input.schoolId(), input);
        List<RowValidationResult> validationResults = validationService.validateRows(input, refData);
        boolean allValid = validationResults.stream().allMatch(RowValidationResult::valid);

        LocalDate applyFrom = LocalDate.parse(input.applyFrom());
        String previewBatchId = "preview_" + IdGenerator.newId();

        int totalPreviewEventCount = 0;
        int totalTeacherScheduleCount = 0;
        int totalPeriodCount = 0;
        List<CalendarRowResultResponse> rowResults = new ArrayList<>();
        List<CalendarEvent> allEvents = new ArrayList<>();
        List<CalendarEventTeacher> allAssignments = new ArrayList<>();
        Map<String, String> clientRowIdByEventId = new HashMap<>();

        for (int i = 0; i < input.rows().size(); i++) {
            CalendarBulkRowRequest row = input.rows().get(i);
            RowValidationResult rowValidation = validationResults.get(i);

            if (!rowValidation.valid()) {
                rowResults.add(new CalendarRowResultResponse(
                        row.clientRowId(), false, 0, 0, rowValidation.errors(), rowValidation.warnings()));
                continue;
            }

            ExpansionResult expansion = expansionService.expand(
                    row, previewBatchId, tenantId, input.schoolId(), userId,
                    applyFrom, input.repeatWeeks(), refData);

            allEvents.addAll(expansion.events());
            allAssignments.addAll(expansion.assignments());
            clientRowIdByEventId.putAll(expansion.clientRowIdByEventId());

            totalPreviewEventCount += expansion.events().size();
            totalTeacherScheduleCount += expansion.assignments().size();
            totalPeriodCount += expansion.events().size() * row.periodCount();

            rowResults.add(new CalendarRowResultResponse(
                    row.clientRowId(), true, expansion.events().size(), expansion.assignments().size(),
                    List.of(), rowValidation.warnings()));
        }

        List<CalendarConflictResponse> conflicts = Collections.emptyList();
        if (allValid && !allEvents.isEmpty()) {
            conflicts = conflictService.detectConflicts(
                    tenantId, allEvents, allAssignments, clientRowIdByEventId);
        }

        boolean previewValid = allValid && conflicts.stream().noneMatch(c -> "ERROR".equals(c.severity()));

        return new CalendarPreviewPayload(previewValid, totalPreviewEventCount,
                totalTeacherScheduleCount, totalPeriodCount, rowResults, conflicts);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarBulkPayload create(
            String tenantId, CalendarBulkCreateRequest input, String userId) {
        input = inputNormalizer.normalize(input);
        String payloadHash = payloadHash(input);

        if (input.idempotencyKey() != null && !input.idempotencyKey().isBlank()) {
            Optional<CalendarBatch> existing =
                    batchRepository.findByTenantIdAndIdempotencyKey(tenantId, input.idempotencyKey());
            if (existing.isPresent()) {
                CalendarBatch batch = existing.get();
                if (batch.getPayloadHash() != null && !payloadHash.equals(batch.getPayloadHash())) {
                    throw new ConflictException(
                            "Idempotency key was already used with a different payload.");
                }
                List<CalendarEvent> existingEvents = findEventsByBatchId(tenantId, batch.getId());
                return buildBulkPayload(batch.getId(), existingEvents, List.of(), List.of());
            }
        }

        boolean lockAcquired = attemptAdvisoryLock(tenantId, input.schoolId());
        if (!lockAcquired) {
            throw new ConflictException("Another bulk schedule operation is in progress for this school.");
        }

        ReferenceData refData = loadReferenceData(tenantId, input.schoolId(), input);
        List<RowValidationResult> validationResults = validationService.validateRows(input, refData);
        boolean allValid = validationResults.stream().allMatch(RowValidationResult::valid);
        if (!allValid) {
            return new CalendarBulkPayload(null, 0, 0, 0, buildRowResults(validationResults, input, refData), List.of());
        }

        LocalDate applyFrom = LocalDate.parse(input.applyFrom());
        String batchId = IdGenerator.newId();

        List<CalendarEvent> allEvents = new ArrayList<>();
        List<CalendarEventTeacher> allAssignments = new ArrayList<>();
        Map<String, String> clientRowIdByEventId = new HashMap<>();
        List<CalendarRowResultResponse> rowResults = new ArrayList<>();

        for (int i = 0; i < input.rows().size(); i++) {
            CalendarBulkRowRequest row = input.rows().get(i);
            ExpansionResult expansion = expansionService.expand(
                    row, batchId, tenantId, input.schoolId(), userId, applyFrom, input.repeatWeeks(), refData);
            allEvents.addAll(expansion.events());
            allAssignments.addAll(expansion.assignments());
            clientRowIdByEventId.putAll(expansion.clientRowIdByEventId());
            rowResults.add(new CalendarRowResultResponse(
                    row.clientRowId(), true, expansion.events().size(), expansion.assignments().size(), List.of(), List.of()));
        }

        List<CalendarConflictResponse> conflicts = conflictService.detectConflicts(
                tenantId, allEvents, allAssignments, clientRowIdByEventId);
        List<CalendarConflictResponse> errorConflicts = conflicts.stream()
                .filter(c -> "ERROR".equals(c.severity())).toList();

        if (!errorConflicts.isEmpty()) {
            if ("DELETE_CONFLICTING".equals(input.resolveMode())) {
                List<String> managedSchoolIds = resolveManagedSchoolIds();
                ResolveResult resolveResult = conflictService.resolveConflictsByDeletion(
                        tenantId, errorConflicts, managedSchoolIds);
                if (!resolveResult.eventIdsToDelete().isEmpty()) {
                    eventRepository.updateStatusBatch(tenantId, new ArrayList<>(resolveResult.eventIdsToDelete()),
                            STATUS_CANCELLED, userId, OffsetDateTime.now());
                }
                Set<String> resolvedExistingIds = resolveResult.resolvedConflicts().stream()
                        .map(CalendarConflictResponse::existingEventId).collect(Collectors.toSet());
                List<CalendarConflictResponse> remainingConflicts = errorConflicts.stream()
                        .filter(c -> !resolvedExistingIds.contains(c.existingEventId())).toList();
                if (!remainingConflicts.isEmpty()) {
                    return new CalendarBulkPayload(null, 0, 0, 0, rowResults, remainingConflicts);
                }
            } else {
                return new CalendarBulkPayload(null, 0, 0, 0, rowResults, conflicts);
            }
        }

        boolean hasWarnings = conflicts.stream().anyMatch(c -> "WARNING".equals(c.severity()));
        if (hasWarnings && !input.overrideConflicts()) {
            return new CalendarBulkPayload(null, 0, 0, 0, rowResults, conflicts);
        }

        CalendarBatch batch = new CalendarBatch();
        batch.setId(batchId);
        batch.setTenantId(tenantId);
        batch.setSchoolId(input.schoolId());
        batch.setAcademicYear(input.academicYear());
        batch.setApplyFrom(applyFrom);
        batch.setRepeatWeeks(input.repeatWeeks());
        batch.setStatus(STATUS_DRAFT);
        batch.setSource("LCMS_BULK_GRID");
        batch.setIdempotencyKey(input.idempotencyKey());
        batch.setPayloadHash(payloadHash);
        batch.setNote(input.note());
        batch.setCreatedBy(userId);
        batchRepository.save(batch);

        if (!conflicts.isEmpty()) {
            conflictService.persistConflicts(tenantId, batchId, conflicts);
        }

        List<CalendarEvent> savedEvents = eventRepository.saveAll(allEvents);
        eventTeacherRepository.saveAll(allAssignments);

        int totalPeriodCount = allEvents.stream()
                .mapToInt(e -> e.getPeriodCount() != null ? e.getPeriodCount() : 0).sum();

        return new CalendarBulkPayload(batchId, savedEvents.size(), allAssignments.size(), totalPeriodCount, rowResults, conflicts);
    }

    String payloadHash(CalendarBulkCreateRequest input) {
        try {
            String canonicalJson = CANONICAL_OBJECT_MAPPER.writeValueAsString(input);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Cannot canonicalize calendar payload.", error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarBatchPayload publishBatch(String tenantId, String batchId, String userId) {
        return batchLifecycle.publish(tenantId, batchId, userId);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarBatchPayload cancelBatch(String tenantId, String batchId, String userId) {
        return batchLifecycle.cancel(tenantId, batchId, userId);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarEventPayload updateEvent(
            String tenantId, CalendarEventUpdateRequest input, String userId) {
        CalendarEvent event = eventRepository.findById(input.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + input.eventId()));
        assertTenant(event.getTenantId(), tenantId);
        if (input.roomId() != null) event.setRoomId(input.roomId());
        if (input.roomName() != null) event.setRoomName(input.roomName());
        if (input.note() != null) event.setNote(input.note());
        event.setUpdatedBy(userId);
        eventRepository.save(event);
        return new CalendarEventPayload(buildEventResponse(event));
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarDeletePayload deleteEvent(
            String tenantId, CalendarEventDeleteRequest input, String userId) {
        CalendarEvent event = eventRepository.findById(input.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + input.eventId()));
        assertTenant(event.getTenantId(), tenantId);
        eventRepository.updateStatusBatch(tenantId, List.of(event.getId()), STATUS_CANCELLED, userId, OffsetDateTime.now());
        return new CalendarDeletePayload(event.getId(), true, 1);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarDeletePayload deleteTeacherSchedule(
            String tenantId, String teacherUserId, List<String> schoolIds,
            String dateFrom, String dateTo, String cancelledBy) {

        LocalDate fromDt = LocalDate.parse(dateFrom);
        LocalDate toDt = LocalDate.parse(dateTo);
        OffsetDateTime from = fromDt.atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
        OffsetDateTime to = toDt.plusDays(1).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();

        Set<String> eventIds = new HashSet<>();
        eventIds.addAll(eventTeacherRepository.findEventIdsByTeacher(
                tenantId, teacherUserId, schoolIds, from, to, STATUS_DRAFT));
        eventIds.addAll(eventTeacherRepository.findEventIdsByTeacher(
                tenantId, teacherUserId, schoolIds, from, to, STATUS_PUBLISHED));

        if (eventIds.isEmpty()) {
            return new CalendarDeletePayload(teacherUserId, true, 0);
        }

        int affected = eventRepository.updateStatusBatch(
                tenantId, new ArrayList<>(eventIds), STATUS_CANCELLED, cancelledBy, OffsetDateTime.now());
        return new CalendarDeletePayload(teacherUserId, true, affected);
    }

    @Transactional("postgresTransactionManager")
    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public CalendarRoomOption createRoom(
            String tenantId, String schoolId, String roomName, String createdBy) {
        String trimmedName = roomName == null ? null : roomName.trim();
        if (trimmedName == null || trimmedName.isBlank()) {
            throw new IllegalArgumentException("Room name must not be blank");
        }

        Optional<CalendarRoom> existing = roomRepository.findBySchoolIdAndNameIgnoreCase(schoolId, trimmedName);
        if (existing.isPresent()) {
            CalendarRoom room = existing.get();
            return new CalendarRoomOption(room.getId(), room.getName(), room.getSchoolId());
        }

        CalendarRoom room = new CalendarRoom();
        room.setTenantId(tenantId);
        room.setSchoolId(schoolId);
        room.setName(trimmedName);
        room.setStatus(STATUS_ACTIVE);
        room.setCreatedBy(createdBy);
        CalendarRoom saved = roomRepository.save(room);
        return new CalendarRoomOption(saved.getId(), saved.getName(), saved.getSchoolId());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private ReferenceData loadReferenceData(String tenantId, String schoolId, CalendarBulkCreateRequest input) {
        List<Classroom> classrooms = classroomRepository.findByTenantIdAndSchoolIdOrderByNameAsc(tenantId, schoolId);
        Map<String, Classroom> classMap = classrooms.stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity()));

        List<Subject> subjects = subjectRepository.findTop500ByTenantIdAndStatusIgnoreCaseOrderByNameAsc(tenantId, STATUS_ACTIVE);
        Map<String, Subject> subjectMap = subjects.stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));

        Set<String> subjectIds = input.rows().stream()
                .map(CalendarBulkRowRequest::subjectId).filter(s -> s != null && !s.isBlank()).collect(Collectors.toSet());
        Map<String, Level> levelMap = new HashMap<>();
        if (!subjectIds.isEmpty()) {
            levelMap = levelRepository
                    .findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                            tenantId, subjectIds, STATUS_ACTIVE)
                    .stream()
                    .collect(Collectors.toMap(Level::getId, Function.identity()));
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
                .filter(roomId -> roomId != null && !roomId.isBlank())
                .collect(Collectors.toSet());
        if (!requestedRoomIds.isEmpty()) {
            Set<String> availableRoomIds = roomRepository
                    .findByTenantIdAndSchoolIdAndStatus(tenantId, schoolId, STATUS_ACTIVE)
                    .stream()
                    .map(CalendarRoom::getId)
                    .collect(Collectors.toSet());
            Set<String> unavailableRoomIds = new LinkedHashSet<>(requestedRoomIds);
            unavailableRoomIds.removeAll(availableRoomIds);
            if (!unavailableRoomIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Room is not active in the selected tenant/school: " + unavailableRoomIds);
            }
        }

        Map<String, EducationUnit> schoolMap = new HashMap<>();
        if (schoolId != null && !schoolId.isBlank()) {
            schoolMap = educationUnitRepository.findByIdInOrderByNameAsc(List.of(schoolId)).stream()
                    .collect(Collectors.toMap(EducationUnit::getId, Function.identity()));
        }

        return new ReferenceData(classMap, subjectMap, levelMap, userMap, schoolMap);
    }

    private List<CalendarRowResultResponse> buildRowResults(
            List<RowValidationResult> validationResults, CalendarBulkCreateRequest input, ReferenceData refData) {
        List<CalendarRowResultResponse> results = new ArrayList<>();
        for (int i = 0; i < validationResults.size(); i++) {
            RowValidationResult rv = validationResults.get(i);
            if (rv.valid()) {
                CalendarBulkRowRequest row = input.rows().get(i);
                LocalDate applyFrom = LocalDate.parse(input.applyFrom());
                ExpansionResult expansion = expansionService.expand(
                        row, "tmp", input.tenantId(), input.schoolId(), "system", applyFrom, input.repeatWeeks(), refData);
                results.add(new CalendarRowResultResponse(
                        rv.clientRowId(), true, expansion.events().size(), expansion.assignments().size(), List.of(), rv.warnings()));
            } else {
                results.add(new CalendarRowResultResponse(
                        rv.clientRowId(), false, 0, 0, rv.errors(), rv.warnings()));
            }
        }
        return results;
    }

    private CalendarBulkPayload buildBulkPayload(
            String batchId, List<CalendarEvent> events,
            List<CalendarRowResultResponse> rowResults, List<CalendarConflictResponse> conflicts) {
        int eventCount = events.size();
        int totalPeriodCount = events.stream()
                .mapToInt(e -> e.getPeriodCount() != null ? e.getPeriodCount() : 0).sum();
        List<String> eventIds = events.stream().map(CalendarEvent::getId).toList();
        List<CalendarEventTeacher> assignments = eventIds.isEmpty() ? List.of()
                : eventTeacherRepository.findByTenantIdAndEventIdIn(events.get(0).getTenantId(), eventIds);
        return new CalendarBulkPayload(batchId, eventCount, assignments.size(), totalPeriodCount, rowResults, conflicts);
    }

    private List<CalendarEvent> findEventsByBatchId(String tenantId, String batchId) {
        return eventRepository.findByTenantIdAndBatchId(tenantId, batchId);
    }

    private boolean attemptAdvisoryLock(String tenantId, String schoolId) {
        try {
            long lockId = (long) (tenantId.hashCode() * 31L + schoolId.hashCode());
            Query query = entityManager.createNativeQuery("SELECT pg_try_advisory_xact_lock(:lockId)");
            query.setParameter("lockId", lockId);
            Object lockResult = query.getSingleResult();
            return Boolean.TRUE.equals(lockResult);
        } catch (Exception e) {
            log.warn("Failed to acquire advisory lock for tenant={} school={}: {}", tenantId, schoolId, e.getMessage());
            return false;
        }
    }

    private List<String> resolveManagedSchoolIds() {
        var principal = accessSupport.currentPrincipal();
        if (principal == null) return List.of();
        String csv = accessSupport.attribute(principal, "managedEducationUnitIds");
        return accessSupport.csv(csv);
    }

    private void assertTenant(String actualTenantId, String expectedTenantId) {
        if (!Objects.equals(actualTenantId, expectedTenantId)) {
            throw new ForbiddenException("Tenant scope does not match the target schedule resource.");
        }
    }

    private CalendarEventResponse buildEventResponse(CalendarEvent event) {
        List<CalendarTeacherAssignmentResponse> teachers =
                eventTeacherRepository.findByTenantIdAndEventIdIn(event.getTenantId(), List.of(event.getId())).stream()
                .map(teacher -> new CalendarTeacherAssignmentResponse(
                        teacher.getTeacherUserId(),
                        teacher.getDisplayNameSnapshot(),
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
                event.getSchoolId(), null,
                event.getAcademicClassId(), null,
                event.getSubjectId(), null,
                event.getLevelId(), null,
                event.getRoomId(), event.getRoomName(),
                event.getPeriodStart() != null ? event.getPeriodStart() : 0,
                event.getPeriodCount() != null ? event.getPeriodCount() : 0,
                teachers, event.getStatus(), event.getNote(), computeColorGroup(event.getSubjectId()));
    }

    private CalendarColorGroupResponse computeColorGroup(String subjectId) {
        if (subjectId == null) return new CalendarColorGroupResponse("default", null, "subject");
        List<String> colorKeys = List.of("blue", "green", "orange", "purple", "red", "teal", "pink", "indigo");
        int index = Math.abs(subjectId.hashCode()) % colorKeys.size();
        return new CalendarColorGroupResponse(colorKeys.get(index), null, "subject");
    }
}
