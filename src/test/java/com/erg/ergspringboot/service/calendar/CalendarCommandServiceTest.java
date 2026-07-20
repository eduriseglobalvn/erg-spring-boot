package com.erg.ergspringboot.service.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBulkPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarConflictResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarDeletePayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPreviewPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOption;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.model.calendar.CalendarRoom;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
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
import com.erg.ergspringboot.service.notifications.NotificationCommand;
import com.erg.ergspringboot.service.notifications.NotificationCommandService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarCommandServiceTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String SCHOOL_ID = "school-001";
    private static final String USER_ID = "user-admin";
    private static final String BATCH_ID = "batch-001";
    private static final String EVENT_ID = "event-001";
    private static final String ACADEMIC_YEAR = "2025-2026";
    private static final String APPLY_FROM = "2026-06-15";
    private static final String IDEMPOTENCY_KEY = "idem-key-001";

    @Mock
    private CalendarBatchRepository batchRepository;
    @Mock
    private CalendarEventRepository eventRepository;
    @Mock
    private CalendarEventTeacherRepository eventTeacherRepository;
    @Mock
    private CalendarRoomRepository roomRepository;
    @Mock
    private CalendarExpansionService expansionService;
    @Mock
    private CalendarValidationService validationService;
    @Mock
    private CalendarConflictService conflictService;
    @Mock
    private NotificationCommandService notificationCommandService;
    @Mock
    private GraphQlAccessSupport accessSupport;
    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private LevelRepository levelRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EducationUnitRepository educationUnitRepository;
    @Mock
    private EntityManager entityManager;

    private CalendarCommandService service;

    @BeforeEach
    void setUp() {
        service = new CalendarCommandService(
                batchRepository, eventRepository, eventTeacherRepository,
                roomRepository, expansionService, validationService, new CalendarBulkInputNormalizer(), conflictService,
                notificationCommandService, accessSupport, classroomRepository,
                subjectRepository, levelRepository, userRepository,
                educationUnitRepository, entityManager);
    }

    // =========================================================================
    // Helper factories
    // =========================================================================

    private CalendarBulkRowRequest createRow(String clientRowId, List<String> classIds) {
        return new CalendarBulkRowRequest(
                clientRowId, classIds, 1, "MORNING", 1, 3,
                "08:00", "10:30", "subj-math", "level-1",
                "room-101", "Room 101",
                List.of("teacher-1"), List.of("teacher-2"),
                "Some note");
    }

    private CalendarBulkCreateRequest createBulkRequest(String resolveMode) {
        return new CalendarBulkCreateRequest(
                TENANT_ID, IDEMPOTENCY_KEY, SCHOOL_ID, ACADEMIC_YEAR,
                APPLY_FROM, 1, "Bulk note", false,
                resolveMode, List.of(createRow("row-1", List.of("class-a"))));
    }

    private CalendarBulkCreateRequest createBulkRequestNoIdempotency() {
        return new CalendarBulkCreateRequest(
                TENANT_ID, null, SCHOOL_ID, ACADEMIC_YEAR,
                APPLY_FROM, 1, "Bulk note", false,
                "REJECT", List.of(createRow("row-1", List.of("class-a"))));
    }

    private CalendarEvent createEvent(String id) {
        CalendarEvent event = new CalendarEvent();
        event.setId(id);
        event.setTenantId(TENANT_ID);
        event.setBatchId(BATCH_ID);
        event.setSchoolId(SCHOOL_ID);
        event.setAcademicClassId("class-a");
        event.setSubjectId("subj-math");
        event.setTitle("Mathematics - Class A");
        event.setStartAt(OffsetDateTime.parse("2026-06-15T08:00:00+07:00"));
        event.setEndAt(OffsetDateTime.parse("2026-06-15T10:30:00+07:00"));
        event.setWeekday((short) 1);
        event.setSession("MORNING");
        event.setPeriodStart((short) 1);
        event.setPeriodCount((short) 3);
        event.setStatus("DRAFT");
        event.setNote("Some note");
        event.setCreatedBy(USER_ID);
        event.setUpdatedBy(USER_ID);
        return event;
    }

    private CalendarEventTeacher createTeacherAssignment(String eventId, String teacherUserId, String role) {
        CalendarEventTeacher assignment = new CalendarEventTeacher();
        assignment.setTenantId(TENANT_ID);
        assignment.setEventId(eventId);
        assignment.setTeacherUserId(teacherUserId);
        assignment.setRole(role);
        return assignment;
    }

    private CalendarBatch createBatch(String status) {
        CalendarBatch batch = new CalendarBatch();
        batch.setId(BATCH_ID);
        batch.setTenantId(TENANT_ID);
        batch.setSchoolId(SCHOOL_ID);
        batch.setAcademicYear(ACADEMIC_YEAR);
        batch.setApplyFrom(LocalDate.parse(APPLY_FROM));
        batch.setRepeatWeeks(1);
        batch.setStatus(status);
        batch.setSource("LCMS_BULK_GRID");
        batch.setIdempotencyKey(IDEMPOTENCY_KEY);
        batch.setNote("Bulk note");
        batch.setCreatedBy(USER_ID);
        return batch;
    }

    @SuppressWarnings("unchecked")
    private <T> TypedQuery<T> mockTypedQuery(List<T> result) {
        TypedQuery<T> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(result);
        return query;
    }

    private void mockReferenceData() {
        Classroom classroom = new Classroom();
        classroom.setId("class-a");
        classroom.setName("Class A");
        classroom.setSchoolId(SCHOOL_ID);

        Subject subject = new Subject();
        subject.setId("subj-math");
        subject.setName("Mathematics");

        when(classroomRepository.findByTenantIdAndSchoolIdOrderByNameAsc(TENANT_ID, SCHOOL_ID))
                .thenReturn(List.of(classroom));
        when(subjectRepository.findTop500ByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE"))
                .thenReturn(List.of(subject));
        when(levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                eq(TENANT_ID), anySet(), eq("ACTIVE")))
                .thenReturn(List.of());
        when(userRepository.findByTenantIdAndIdIn(eq(TENANT_ID), anySet())).thenReturn(List.of());
        when(educationUnitRepository.findByIdInOrderByNameAsc(List.of(SCHOOL_ID)))
                .thenReturn(List.of());
        CalendarRoom room = new CalendarRoom();
        room.setId("room-101");
        room.setTenantId(TENANT_ID);
        room.setSchoolId(SCHOOL_ID);
        room.setStatus("ACTIVE");
        when(roomRepository.findByTenantIdAndSchoolIdAndStatus(TENANT_ID, SCHOOL_ID, "ACTIVE"))
                .thenReturn(List.of(room));
    }

    private void mockLockQuery(boolean acquired) {
        Query lockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(lockQuery);
        when(lockQuery.setParameter(anyString(), any())).thenReturn(lockQuery);
        when(lockQuery.getSingleResult()).thenReturn(acquired);
    }

    // =========================================================================
    // 1. preview - generates preview with expansion and conflict detection
    // =========================================================================

    @Nested
    class PreviewTest {

        @Test
        void rejectsRoomOutsideTenantSchoolScope() {
            CalendarBulkCreateRequest input = createBulkRequestNoIdempotency();
            when(roomRepository.findByTenantIdAndSchoolIdAndStatus(TENANT_ID, SCHOOL_ID, "ACTIVE"))
                    .thenReturn(List.of());

            assertThrows(IllegalArgumentException.class,
                    () -> service.preview(TENANT_ID, input, USER_ID),
                    "Room outside the selected tenant/school must be rejected");
        }

        @Test
        void shouldGeneratePreviewWithExpansionAndConflictDetection() {
            CalendarBulkCreateRequest input = createBulkRequestNoIdempotency();
            CalendarBulkRowRequest row = input.rows().get(0);

            mockReferenceData();

            when(validationService.validateRows(eq(input), any()))
                    .thenReturn(List.of(new RowValidationResult("row-1", true, List.of(), List.of())));

            CalendarEvent event = createEvent("preview-evt-1");
            ExpansionResult expansion = new ExpansionResult(
                    List.of(event),
                    List.of(createTeacherAssignment("preview-evt-1", "teacher-1", "MAIN")));
            when(expansionService.expand(eq(row), anyString(), eq(TENANT_ID), eq(SCHOOL_ID),
                    eq(USER_ID), eq(LocalDate.parse(APPLY_FROM)), eq(1), any()))
                    .thenReturn(expansion);

            when(conflictService.detectConflicts(eq(TENANT_ID), anyList(), anyList(), anyMap()))
                    .thenReturn(List.of());

            CalendarPreviewPayload payload = service.preview(TENANT_ID, input, USER_ID);

            assertNotNull(payload);
            assertTrue(payload.valid());
            assertEquals(1, payload.previewEventCount());
            assertEquals(1, payload.teacherScheduleCount());
            assertEquals(3, payload.totalPeriodCount());
            assertEquals(1, payload.rowResults().size());
            assertTrue(payload.rowResults().get(0).valid());
            assertTrue(payload.conflicts().isEmpty());
            verify(userRepository).findByTenantIdAndIdIn(
                    TENANT_ID, Set.of("teacher-1", "teacher-2"));
        }

        @Test
        void shouldReturnInvalidPreviewWhenConflictsHaveErrorSeverity() {
            CalendarBulkCreateRequest input = createBulkRequestNoIdempotency();
            CalendarBulkRowRequest row = input.rows().get(0);

            mockReferenceData();

            when(validationService.validateRows(eq(input), any()))
                    .thenReturn(List.of(new RowValidationResult("row-1", true, List.of(), List.of())));

            ExpansionResult expansion = new ExpansionResult(
                    List.of(createEvent("preview-evt-1")),
                    List.of(createTeacherAssignment("preview-evt-1", "teacher-1", "MAIN")));
            when(expansionService.expand(eq(row), anyString(), eq(TENANT_ID), eq(SCHOOL_ID),
                    eq(USER_ID), eq(LocalDate.parse(APPLY_FROM)), eq(1), any()))
                    .thenReturn(expansion);

            CalendarConflictResponse errorConflict = new CalendarConflictResponse(
                    "CLASS_TIME_CONFLICT", "ERROR", "row-1", "existing-evt-1", "Class time conflict");
            when(conflictService.detectConflicts(eq(TENANT_ID), anyList(), anyList(), anyMap()))
                    .thenReturn(List.of(errorConflict));

            CalendarPreviewPayload payload = service.preview(TENANT_ID, input, USER_ID);

            assertFalse(payload.valid());
            assertEquals(1, payload.conflicts().size());
            assertEquals("CLASS_TIME_CONFLICT", payload.conflicts().get(0).code());
            verify(conflictService, never()).persistConflicts(anyString(), anyString(), anyList());
        }
    }

    // =========================================================================
    // 2. create - successful creation flow with batch persist
    // =========================================================================

    @Nested
    class CreateSuccessTest {

        @Test
        void shouldCreateBatchAndPersistEventsSuccessfully() {
            CalendarBulkCreateRequest input = createBulkRequestNoIdempotency();
            CalendarBulkRowRequest row = input.rows().get(0);

            // No idempotency key -> skip idempotency check
            // Advisory lock succeeds
            mockLockQuery(true);

            mockReferenceData();

            when(validationService.validateRows(eq(input), any()))
                    .thenReturn(List.of(new RowValidationResult("row-1", true, List.of(), List.of())));

            CalendarEvent event = createEvent(EVENT_ID);
            ExpansionResult expansion = new ExpansionResult(
                    List.of(event),
                    List.of(createTeacherAssignment(EVENT_ID, "teacher-1", "MAIN")));
            when(expansionService.expand(eq(row), anyString(), eq(TENANT_ID), eq(SCHOOL_ID),
                    eq(USER_ID), eq(LocalDate.parse(APPLY_FROM)), eq(1), any()))
                    .thenReturn(expansion);

            when(conflictService.detectConflicts(eq(TENANT_ID), anyList(), anyList(), anyMap()))
                    .thenReturn(List.of());

            when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(eventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(eventTeacherRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            CalendarBulkPayload payload = service.create(TENANT_ID, input, USER_ID);

            assertNotNull(payload);
            assertNotNull(payload.batchId());
            assertEquals(1, payload.createdEventCount());
            assertEquals(1, payload.teacherScheduleCount());
            assertEquals(3, payload.totalPeriodCount());
            assertTrue(payload.conflicts().isEmpty());

            verify(batchRepository).save(any(CalendarBatch.class));
            verify(eventRepository).saveAll(anyList());
            verify(eventTeacherRepository).saveAll(anyList());
        }

        @Test
        void shouldThrowWhenLockNotAcquired() {
            CalendarBulkCreateRequest input = createBulkRequestNoIdempotency();

            mockLockQuery(false);

            assertThrows(ConflictException.class,
                    () -> service.create(TENANT_ID, input, USER_ID));
        }
    }

    // =========================================================================
    // 3. create - idempotency (same key+payload returns old result)
    // =========================================================================

    @Nested
    class CreateIdempotencyTest {

        @Test
        void shouldRejectReusedIdempotencyKeyWithDifferentPayload() {
            CalendarBulkCreateRequest input = createBulkRequest("REJECT");
            CalendarBatch batch = createBatch("DRAFT");
            batch.setPayloadHash("different-payload-hash");
            when(batchRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, IDEMPOTENCY_KEY))
                    .thenReturn(Optional.of(batch));

            assertThrows(ConflictException.class,
                    () -> service.create(TENANT_ID, input, USER_ID));

            verifyNoInteractions(expansionService);
        }

        @Test
        void shouldReturnExistingBatchWhenIdempotencyKeyMatches() {
            CalendarBulkCreateRequest input = createBulkRequest("REJECT");
            CalendarBatch batch = createBatch("DRAFT");
            batch.setPayloadHash(service.payloadHash(input));
            CalendarEvent event = createEvent(EVENT_ID);

            when(batchRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, IDEMPOTENCY_KEY))
                    .thenReturn(Optional.of(batch));

            // Mock findEventsByBatchId
            when(eventRepository.findByTenantIdAndBatchId(TENANT_ID, BATCH_ID))
                    .thenReturn(List.of(event));

            when(eventTeacherRepository.findByTenantIdAndEventIdIn(TENANT_ID, List.of(EVENT_ID)))
                    .thenReturn(List.of(createTeacherAssignment(EVENT_ID, "teacher-1", "MAIN")));

            CalendarBulkPayload payload = service.create(TENANT_ID, input, USER_ID);

            assertNotNull(payload);
            assertEquals(BATCH_ID, payload.batchId());
            assertEquals(1, payload.createdEventCount());
            assertEquals(1, payload.teacherScheduleCount());

            verify(batchRepository, never()).save(any());
            verify(eventRepository, never()).saveAll(any());
            verify(expansionService, never())
                    .expand(any(), any(), any(), any(), any(), any(), anyInt(), any());
        }
    }

    // =========================================================================
    // 4. create - conflict detection leading to REJECT
    // =========================================================================

    @Nested
    class CreateConflictRejectTest {

        @Test
        void shouldRequireOverrideBeforeCreatingBatchWithWarnings() {
            CalendarBulkCreateRequest input = createBulkRequest("REJECT");
            CalendarBulkRowRequest row = input.rows().get(0);
            when(batchRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, IDEMPOTENCY_KEY))
                    .thenReturn(Optional.empty());
            mockLockQuery(true);
            mockReferenceData();
            when(validationService.validateRows(eq(input), any()))
                    .thenReturn(List.of(new RowValidationResult("row-1", true, List.of(), List.of())));
            CalendarEvent event = createEvent(EVENT_ID);
            when(expansionService.expand(eq(row), anyString(), eq(TENANT_ID), eq(SCHOOL_ID),
                    eq(USER_ID), eq(LocalDate.parse(APPLY_FROM)), eq(1), any()))
                    .thenReturn(new ExpansionResult(List.of(event), List.of(), Map.of(EVENT_ID, "row-1")));
            CalendarConflictResponse warning = new CalendarConflictResponse(
                    "ROOM_TIME_CONFLICT", "WARNING", "row-1", "existing-evt-1", "Room conflict");
            when(conflictService.detectConflicts(eq(TENANT_ID), anyList(), anyList(), anyMap()))
                    .thenReturn(List.of(warning));

            CalendarBulkPayload payload = service.create(TENANT_ID, input, USER_ID);

            assertNull(payload.batchId());
            assertEquals(List.of(warning), payload.conflicts());
            verify(batchRepository, never()).save(any());
            verify(conflictService, never()).persistConflicts(anyString(), anyString(), anyList());
        }

        @Test
        void shouldReturnErrorConflictsWhenResolveModeIsReject() {
            CalendarBulkCreateRequest input = createBulkRequest("REJECT");
            CalendarBulkRowRequest row = input.rows().get(0);

            when(batchRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, IDEMPOTENCY_KEY))
                    .thenReturn(Optional.empty());

            mockLockQuery(true);
            mockReferenceData();

            when(validationService.validateRows(eq(input), any()))
                    .thenReturn(List.of(new RowValidationResult("row-1", true, List.of(), List.of())));

            CalendarEvent event = createEvent(EVENT_ID);
            ExpansionResult expansion = new ExpansionResult(
                    List.of(event),
                    List.of(createTeacherAssignment(EVENT_ID, "teacher-1", "MAIN")));
            when(expansionService.expand(eq(row), anyString(), eq(TENANT_ID), eq(SCHOOL_ID),
                    eq(USER_ID), eq(LocalDate.parse(APPLY_FROM)), eq(1), any()))
                    .thenReturn(expansion);

            CalendarConflictResponse errorConflict = new CalendarConflictResponse(
                    "CLASS_TIME_CONFLICT", "ERROR", "row-1", "existing-evt-1",
                    "Class time conflict");
            when(conflictService.detectConflicts(eq(TENANT_ID), anyList(), anyList(), anyMap()))
                    .thenReturn(List.of(errorConflict));

            CalendarBulkPayload payload = service.create(TENANT_ID, input, USER_ID);

            assertNull(payload.batchId());
            assertEquals(0, payload.createdEventCount());
            assertEquals(1, payload.conflicts().size());
            assertEquals("CLASS_TIME_CONFLICT", payload.conflicts().get(0).code());

            verify(batchRepository, never()).save(any());
            verify(eventRepository, never()).saveAll(any());
        }
    }

    // =========================================================================
    // 5. create - DELETE_CONFLICTING resolve mode
    // =========================================================================

    @Nested
    class CreateDeleteConflictingTest {

        @Test
        void shouldResolveConflictsByDeletionAndProceedToPersist() {
            CalendarBulkCreateRequest input = createBulkRequest("DELETE_CONFLICTING");
            CalendarBulkRowRequest row = input.rows().get(0);

            when(batchRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, IDEMPOTENCY_KEY))
                    .thenReturn(Optional.empty());

            mockLockQuery(true);
            mockReferenceData();

            when(validationService.validateRows(eq(input), any()))
                    .thenReturn(List.of(new RowValidationResult("row-1", true, List.of(), List.of())));

            CalendarEvent event = createEvent(EVENT_ID);
            ExpansionResult expansion = new ExpansionResult(
                    List.of(event),
                    List.of(createTeacherAssignment(EVENT_ID, "teacher-1", "MAIN")));
            when(expansionService.expand(eq(row), anyString(), eq(TENANT_ID), eq(SCHOOL_ID),
                    eq(USER_ID), eq(LocalDate.parse(APPLY_FROM)), eq(1), any()))
                    .thenReturn(expansion);

            CalendarConflictResponse errorConflict = new CalendarConflictResponse(
                    "TEACHER_TIME_CONFLICT", "ERROR", "row-1", "existing-evt-1",
                    "Teacher conflict");
            when(conflictService.detectConflicts(eq(TENANT_ID), anyList(), anyList(), anyMap()))
                    .thenReturn(List.of(errorConflict));

            // Mock resolveManagedSchoolIds
            ErgPrincipal principal = mock(ErgPrincipal.class);
            when(accessSupport.currentPrincipal()).thenReturn(principal);
            when(accessSupport.attribute(principal, "managedEducationUnitIds"))
                    .thenReturn("school-001,school-002");
            when(accessSupport.csv("school-001,school-002"))
                    .thenReturn(List.of("school-001", "school-002"));

            ResolveResult resolveResult = new ResolveResult(
                    List.of(errorConflict), Set.of("existing-evt-1"));
            when(conflictService.resolveConflictsByDeletion(
                    eq(TENANT_ID), anyList(), eq(List.of("school-001", "school-002"))))
                    .thenReturn(resolveResult);

            when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(eventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(eventTeacherRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            CalendarBulkPayload payload = service.create(TENANT_ID, input, USER_ID);

            assertNotNull(payload.batchId());
            assertEquals(1, payload.createdEventCount());

            verify(eventRepository).updateStatusBatch(
                    eq(TENANT_ID), eq(List.of("existing-evt-1")), eq("CANCELLED"), eq(USER_ID), any());
            verify(batchRepository).save(any(CalendarBatch.class));
            verify(eventRepository).saveAll(anyList());
            verify(eventTeacherRepository).saveAll(anyList());
        }

        @Test
        void shouldReturnRemainingConflictsWhenResolutionIncomplete() {
            CalendarBulkCreateRequest input = createBulkRequest("DELETE_CONFLICTING");
            CalendarBulkRowRequest row = input.rows().get(0);

            when(batchRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, IDEMPOTENCY_KEY))
                    .thenReturn(Optional.empty());

            mockLockQuery(true);
            mockReferenceData();

            when(validationService.validateRows(eq(input), any()))
                    .thenReturn(List.of(new RowValidationResult("row-1", true, List.of(), List.of())));

            CalendarEvent event = createEvent(EVENT_ID);
            ExpansionResult expansion = new ExpansionResult(
                    List.of(event),
                    List.of(createTeacherAssignment(EVENT_ID, "teacher-1", "MAIN")));
            when(expansionService.expand(eq(row), anyString(), eq(TENANT_ID), eq(SCHOOL_ID),
                    eq(USER_ID), eq(LocalDate.parse(APPLY_FROM)), eq(1), any()))
                    .thenReturn(expansion);

            CalendarConflictResponse resolvedConflict = new CalendarConflictResponse(
                    "TEACHER_TIME_CONFLICT", "ERROR", "row-1", "existing-evt-1",
                    "Teacher conflict");
            CalendarConflictResponse unresolvedConflict = new CalendarConflictResponse(
                    "CLASS_TIME_CONFLICT", "ERROR", "row-1", "existing-evt-2",
                    "Class conflict");
            when(conflictService.detectConflicts(eq(TENANT_ID), anyList(), anyList(), anyMap()))
                    .thenReturn(List.of(resolvedConflict, unresolvedConflict));

            ErgPrincipal principal = mock(ErgPrincipal.class);
            when(accessSupport.currentPrincipal()).thenReturn(principal);
            when(accessSupport.attribute(principal, "managedEducationUnitIds"))
                    .thenReturn("school-001");
            when(accessSupport.csv("school-001"))
                    .thenReturn(List.of("school-001"));

            ResolveResult resolveResult = new ResolveResult(
                    List.of(resolvedConflict), Set.of("existing-evt-1"));
            when(conflictService.resolveConflictsByDeletion(
                    eq(TENANT_ID), anyList(), eq(List.of("school-001"))))
                    .thenReturn(resolveResult);

            CalendarBulkPayload payload = service.create(TENANT_ID, input, USER_ID);

            assertNull(payload.batchId());
            assertFalse(payload.conflicts().isEmpty());
            assertEquals(1, payload.conflicts().size());
            assertEquals("CLASS_TIME_CONFLICT", payload.conflicts().get(0).code());

            verify(eventRepository).updateStatusBatch(
                    eq(TENANT_ID), eq(List.of("existing-evt-1")), eq("CANCELLED"), eq(USER_ID), any());
            verify(batchRepository, never()).save(any());
            verify(eventRepository, never()).saveAll(anyList());
        }
    }

    // =========================================================================
    // 6. publishBatch - status update + notification
    // =========================================================================

    @Nested
    class PublishBatchTest {

        @Test
        void shouldPublishBatchAndEmitNotification() {
            CalendarBatch batch = createBatch("DRAFT");
            CalendarEvent event = createEvent(EVENT_ID);

            when(batchRepository.findByTenantIdAndId(TENANT_ID, BATCH_ID)).thenReturn(Optional.of(batch));

            when(eventRepository.findByTenantIdAndBatchId(TENANT_ID, BATCH_ID))
                    .thenReturn(List.of(event));

            when(eventRepository.updateStatusBatch(
                    eq(TENANT_ID), eq(List.of(EVENT_ID)), eq("PUBLISHED"), eq(USER_ID), any()))
                    .thenReturn(1);

            when(eventTeacherRepository.findByTenantIdAndEventIdIn(TENANT_ID, List.of(EVENT_ID)))
                    .thenReturn(List.of(createTeacherAssignment(EVENT_ID, "teacher-1", "MAIN")));

            CalendarBatchPayload payload = service.publishBatch(TENANT_ID, BATCH_ID, USER_ID);

            assertEquals(BATCH_ID, payload.batchId());
            assertEquals("PUBLISHED", payload.status());
            assertEquals(1, payload.affectedEventCount());

            verify(batchRepository).save(batch);
            assertEquals("PUBLISHED", batch.getStatus());
            assertEquals(USER_ID, batch.getPublishedBy());
            assertNotNull(batch.getPublishedAt());

            verify(eventRepository).updateStatusBatch(
                    eq(TENANT_ID), eq(List.of(EVENT_ID)), eq("PUBLISHED"), eq(USER_ID), any());
            verify(notificationCommandService).enqueue(argThat(command ->
                    ("schedule-published:" + BATCH_ID).equals(command.idempotencyKey())
                            && "/calendar".equals(command.actionUrl())));
        }

        @Test
        void shouldRejectPublishingAnEmptyBatch() {
            when(batchRepository.findByTenantIdAndId(TENANT_ID, BATCH_ID))
                    .thenReturn(Optional.of(createBatch("DRAFT")));
            when(eventRepository.findByTenantIdAndBatchId(TENANT_ID, BATCH_ID))
                    .thenReturn(List.of());

            assertThrows(IllegalStateException.class,
                    () -> service.publishBatch(TENANT_ID, BATCH_ID, USER_ID));

            verify(batchRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenBatchNotFound() {
            when(batchRepository.findByTenantIdAndId(TENANT_ID, BATCH_ID)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> service.publishBatch(TENANT_ID, BATCH_ID, USER_ID));
        }

        @Test
        void shouldRejectCancellingAnAlreadyCancelledBatch() {
            when(batchRepository.findByTenantIdAndId(TENANT_ID, BATCH_ID))
                    .thenReturn(Optional.of(createBatch("CANCELLED")));

            assertThrows(IllegalStateException.class,
                    () -> service.cancelBatch(TENANT_ID, BATCH_ID, USER_ID));

            verify(batchRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenBatchNotInDraftStatus() {
            when(batchRepository.findByTenantIdAndId(TENANT_ID, BATCH_ID))
                    .thenReturn(Optional.of(createBatch("PUBLISHED")));

            assertThrows(IllegalStateException.class,
                    () -> service.publishBatch(TENANT_ID, BATCH_ID, USER_ID));
        }
    }

    // =========================================================================
    // 7. cancelBatch - status update
    // =========================================================================

    @Nested
    class CancelBatchTest {

        @Test
        void shouldCancelBatchAndUpdateEventStatuses() {
            CalendarBatch batch = createBatch("DRAFT");

            when(batchRepository.findByTenantIdAndId(TENANT_ID, BATCH_ID)).thenReturn(Optional.of(batch));

            when(eventRepository.findByTenantIdAndBatchId(TENANT_ID, BATCH_ID))
                    .thenReturn(List.of(createEvent(EVENT_ID)));

            when(eventRepository.updateStatusBatch(
                    eq(TENANT_ID), eq(List.of(EVENT_ID)), eq("CANCELLED"), eq(USER_ID), any()))
                    .thenReturn(1);

            CalendarBatchPayload payload = service.cancelBatch(TENANT_ID, BATCH_ID, USER_ID);

            assertEquals(BATCH_ID, payload.batchId());
            assertEquals("CANCELLED", payload.status());
            assertEquals(1, payload.affectedEventCount());

            verify(batchRepository).save(batch);
            assertEquals("CANCELLED", batch.getStatus());
        }

        @Test
        void shouldThrowWhenBatchNotFound() {
            when(batchRepository.findByTenantIdAndId(TENANT_ID, BATCH_ID)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> service.cancelBatch(TENANT_ID, BATCH_ID, USER_ID));
        }
    }

    // =========================================================================
    // 8. updateEvent - partial update (roomId, roomName, note)
    // =========================================================================

    @Nested
    class UpdateEventTest {

        @Test
        void shouldUpdateAllProvidedFields() {
            CalendarEvent event = createEvent(EVENT_ID);
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
            when(eventTeacherRepository.findByTenantIdAndEventIdIn(TENANT_ID, List.of(EVENT_ID))).thenReturn(List.of());

            CalendarEventUpdateRequest request = new CalendarEventUpdateRequest(
                    TENANT_ID, EVENT_ID, "room-200", "Room 200", "Updated note");

            CalendarEventPayload payload = service.updateEvent(TENANT_ID, request, USER_ID);

            assertNotNull(payload);
            assertNotNull(payload.event());
            assertEquals("room-200", event.getRoomId());
            assertEquals("Room 200", event.getRoomName());
            assertEquals("Updated note", event.getNote());
            assertEquals(USER_ID, event.getUpdatedBy());

            verify(eventRepository).save(event);
        }

        @Test
        void shouldKeepExistingValuesWhenFieldsAreNull() {
            CalendarEvent event = createEvent(EVENT_ID);
            event.setRoomId("room-101");
            event.setRoomName("Room 101");
            event.setNote("Original note");
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
            when(eventTeacherRepository.findByTenantIdAndEventIdIn(TENANT_ID, List.of(EVENT_ID))).thenReturn(List.of());

            CalendarEventUpdateRequest request = new CalendarEventUpdateRequest(
                    TENANT_ID, EVENT_ID, null, null, null);

            service.updateEvent(TENANT_ID, request, USER_ID);

            assertEquals("room-101", event.getRoomId());
            assertEquals("Room 101", event.getRoomName());
            assertEquals("Original note", event.getNote());
            assertEquals(USER_ID, event.getUpdatedBy());

            verify(eventRepository).save(event);
        }

        @Test
        void shouldThrowWhenEventNotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            CalendarEventUpdateRequest request = new CalendarEventUpdateRequest(
                    TENANT_ID, EVENT_ID, "room-200", null, null);

            assertThrows(IllegalArgumentException.class,
                    () -> service.updateEvent(TENANT_ID, request, USER_ID));
        }
    }

    // =========================================================================
    // 9. deleteEvent - cancel status
    // =========================================================================

    @Nested
    class DeleteEventTest {

        @Test
        void shouldCancelSingleEvent() {
            CalendarEvent event = createEvent(EVENT_ID);
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

            CalendarEventDeleteRequest request = new CalendarEventDeleteRequest(TENANT_ID, EVENT_ID);

            CalendarDeletePayload payload = service.deleteEvent(TENANT_ID, request, USER_ID);

            assertEquals(EVENT_ID, payload.eventId());
            assertTrue(payload.deleted());
            assertEquals(1, payload.affectedEventCount());

            verify(eventRepository).updateStatusBatch(
                    eq(TENANT_ID), eq(List.of(EVENT_ID)), eq("CANCELLED"), eq(USER_ID), any());
        }

        @Test
        void shouldThrowWhenEventNotFound() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

            CalendarEventDeleteRequest request = new CalendarEventDeleteRequest(TENANT_ID, EVENT_ID);

            assertThrows(IllegalArgumentException.class,
                    () -> service.deleteEvent(TENANT_ID, request, USER_ID));
        }
    }

    // =========================================================================
    // 10. deleteTeacherSchedule - bulk cancel by teacher
    // =========================================================================

    @Nested
    class DeleteTeacherScheduleTest {

        @Test
        void shouldCancelAllEventsForTeacherInDateRange() {
            when(eventTeacherRepository.findEventIdsByTeacher(
                    eq(TENANT_ID), eq("teacher-1"), eq(List.of(SCHOOL_ID)),
                    any(), any(), eq("DRAFT")))
                    .thenReturn(List.of("evt-1", "evt-2"));
            when(eventTeacherRepository.findEventIdsByTeacher(
                    eq(TENANT_ID), eq("teacher-1"), eq(List.of(SCHOOL_ID)),
                    any(), any(), eq("PUBLISHED")))
                    .thenReturn(List.of("evt-3"));

            when(eventRepository.updateStatusBatch(eq(TENANT_ID), anyList(), eq("CANCELLED"), eq(USER_ID), any()))
                    .thenReturn(3);

            CalendarDeletePayload payload = service.deleteTeacherSchedule(
                    TENANT_ID, "teacher-1", List.of(SCHOOL_ID),
                    "2026-06-15", "2026-06-21", USER_ID);

            assertEquals("teacher-1", payload.eventId());
            assertTrue(payload.deleted());
            assertEquals(3, payload.affectedEventCount());

            verify(eventRepository).updateStatusBatch(
                    eq(TENANT_ID),
                    argThat(ids -> ids.containsAll(List.of("evt-1", "evt-2", "evt-3"))
                            && ids.size() == 3),
                    eq("CANCELLED"), eq(USER_ID), any());
        }

        @Test
        void shouldReturnZeroWhenNoEventsFound() {
            when(eventTeacherRepository.findEventIdsByTeacher(
                    anyString(), anyString(), anyList(), any(), any(), anyString()))
                    .thenReturn(List.of());

            CalendarDeletePayload payload = service.deleteTeacherSchedule(
                    TENANT_ID, "teacher-1", List.of(SCHOOL_ID),
                    "2026-06-15", "2026-06-21", USER_ID);

            assertEquals("teacher-1", payload.eventId());
            assertTrue(payload.deleted());
            assertEquals(0, payload.affectedEventCount());

            verify(eventRepository, never())
                    .updateStatusBatch(anyString(), anyList(), anyString(), anyString(), any());
        }
    }

    // =========================================================================
    // 11. createRoom - idempotent (existing returns, new creates)
    // =========================================================================

    @Nested
    class CreateRoomTest {

        @Test
        void shouldCreateNewRoom() {
            when(roomRepository.findBySchoolIdAndNameIgnoreCase(SCHOOL_ID, "New Room"))
                    .thenReturn(Optional.empty());

            CalendarRoom savedRoom = new CalendarRoom();
            savedRoom.setId("room-new");
            savedRoom.setTenantId(TENANT_ID);
            savedRoom.setSchoolId(SCHOOL_ID);
            savedRoom.setName("New Room");
            savedRoom.setStatus("ACTIVE");
            savedRoom.setCreatedBy(USER_ID);

            when(roomRepository.save(any())).thenReturn(savedRoom);

            CalendarRoomOption option = service.createRoom(
                    TENANT_ID, SCHOOL_ID, "  New Room  ", USER_ID);

            assertEquals("room-new", option.id());
            assertEquals("New Room", option.label());
            assertEquals(SCHOOL_ID, option.schoolId());

            verify(roomRepository).save(any());
        }

        @Test
        void shouldReturnExistingRoomWhenFoundByName() {
            CalendarRoom existingRoom = new CalendarRoom();
            existingRoom.setId("room-existing");
            existingRoom.setName("Room 101");
            existingRoom.setSchoolId(SCHOOL_ID);

            when(roomRepository.findBySchoolIdAndNameIgnoreCase(SCHOOL_ID, "Room 101"))
                    .thenReturn(Optional.of(existingRoom));

            CalendarRoomOption option = service.createRoom(
                    TENANT_ID, SCHOOL_ID, "Room 101", USER_ID);

            assertEquals("room-existing", option.id());
            assertEquals("Room 101", option.label());

            verify(roomRepository, never()).save(any());
        }

        @Test
        void shouldTrimRoomNameAndMatchExisting() {
            CalendarRoom existingRoom = new CalendarRoom();
            existingRoom.setId("room-existing");
            existingRoom.setName("Room 101");
            existingRoom.setSchoolId(SCHOOL_ID);

            when(roomRepository.findBySchoolIdAndNameIgnoreCase(SCHOOL_ID, "Room 101"))
                    .thenReturn(Optional.of(existingRoom));

            CalendarRoomOption option = service.createRoom(
                    TENANT_ID, SCHOOL_ID, "   Room 101   ", USER_ID);

            assertEquals("room-existing", option.id());
            verify(roomRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenRoomNameIsBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.createRoom(TENANT_ID, SCHOOL_ID, "   ", USER_ID));
            assertThrows(IllegalArgumentException.class,
                    () -> service.createRoom(TENANT_ID, SCHOOL_ID, null, USER_ID));
            assertThrows(IllegalArgumentException.class,
                    () -> service.createRoom(TENANT_ID, SCHOOL_ID, "", USER_ID));

            verify(roomRepository, never()).save(any());
        }
    }
}
