package com.erg.ergspringboot.service.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarCatalogResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarFilterOptionResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarFilterOptionsResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarLaneSummaryResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPermissionsResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarTeacherAssignmentResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.model.calendar.CalendarRoom;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.concurrency.ErgReadTaskExecutor;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import com.erg.ergspringboot.repository.calendar.CalendarRoomRepository;
import com.erg.ergspringboot.repository.calendar.projection.CalendarTeacherColorProjection;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.LevelRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarReadServiceTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String SCHOOL_ID_A = "school-a";
    private static final String SCHOOL_ID_B = "school-b";
    private static final String CLASS_ID_A = "class-a";
    private static final String CLASS_ID_B = "class-b";
    private static final String SUBJECT_ID_A = "subj-a";
    private static final String SUBJECT_ID_B = "subj-b";
    private static final String LEVEL_ID_A = "level-a";
    private static final String LEVEL_ID_B = "level-b";
    private static final String TEACHER_USER_ID_A = "teacher-a";
    private static final String TEACHER_USER_ID_B = "teacher-b";
    private static final String EVENT_ID_A = "event-a";
    private static final String EVENT_ID_B = "event-b";
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-06-15T07:00:00+07:00");

    @Mock private CalendarEventRepository eventRepository;
    @Mock private CalendarEventTeacherRepository eventTeacherRepository;
    @Mock private ClassroomRepository classroomRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private LevelRepository levelRepository;
    @Mock private EducationUnitRepository educationUnitRepository;
    @Mock private CalendarRoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private CalendarBatchRepository batchRepository;
    private ErgReadTaskExecutor readTaskExecutor;

    private CalendarReadService service;

    @BeforeEach
    void setUp() {
        readTaskExecutor = new ErgReadTaskExecutor(4, 4, 32, Duration.ofSeconds(1));
        service = new CalendarReadService(
                eventRepository,
                eventTeacherRepository,
                classroomRepository,
                subjectRepository,
                levelRepository,
                educationUnitRepository,
                roomRepository,
                userRepository,
                studentRepository,
                batchRepository,
                readTaskExecutor);
        // Existing fixtures stub the pre-bounded methods; bridge them to the new DB-limited overloads.
        lenient().when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(
                anyString(), anyList(), any(Pageable.class)))
                .thenAnswer(invocation -> classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(
                        invocation.getArgument(0), invocation.getArgument(1)));
        lenient().when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(
                anyString(), anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(
                        invocation.getArgument(0), invocation.getArgument(1)));
        lenient().when(roomRepository.findByTenantIdAndSchoolIdInAndStatus(
                anyString(), anyList(), anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> roomRepository.findByTenantIdAndSchoolIdInAndStatus(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        lenient().when(levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                anyString(), anySet(), anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        lenient().when(userRepository.findCalendarTeachersByTenant(anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> userRepository.findCalendarTeachersByTenant(invocation.getArgument(0)));
        lenient().when(userRepository.findCalendarTeachersByTenantAndSchoolIds(
                anyString(), anyList(), any(Pageable.class)))
                .thenAnswer(invocation -> userRepository.findCalendarTeachersByTenantAndSchoolIds(
                        invocation.getArgument(0), invocation.getArgument(1)));
        lenient().when(classroomRepository.findHomeroomTeacherIdsByTenant(anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> classroomRepository.findHomeroomTeacherIdsByTenant(invocation.getArgument(0)));
        lenient().when(classroomRepository.findHomeroomTeacherIdsByTenantAndSchoolIds(
                anyString(), anyList(), any(Pageable.class)))
                .thenAnswer(invocation -> classroomRepository.findHomeroomTeacherIdsByTenantAndSchoolIds(
                        invocation.getArgument(0), invocation.getArgument(1)));
    }

    @AfterEach
    void tearDown() {
        readTaskExecutor.close();
    }

    // =========================================================================
    // Helper factories
    // =========================================================================

    private CalendarEvent event(String id, String schoolId, String classId, String subjectId,
                                String levelId, OffsetDateTime start, OffsetDateTime end, String status) {
        CalendarEvent e = new CalendarEvent();
        e.setId(id);
        e.setTenantId(TENANT_ID);
        e.setBatchId("batch-default");
        e.setSchoolId(schoolId);
        e.setAcademicClassId(classId);
        e.setSubjectId(subjectId);
        e.setLevelId(levelId);
        e.setTitle("Test Event");
        e.setStartAt(start);
        e.setEndAt(end);
        e.setWeekday((short) start.getDayOfWeek().getValue());
        e.setPeriodStart((short) 1);
        e.setPeriodCount((short) 2);
        e.setStatus(status);
        e.setRoomId("room-1");
        e.setRoomName("Room 101");
        return e;
    }

    private CalendarEventTeacher assignment(String eventId, String teacherUserId, String role,
                                            String displayName, String colorKey) {
        CalendarEventTeacher t = new CalendarEventTeacher();
        t.setTenantId(TENANT_ID);
        t.setEventId(eventId);
        t.setTeacherUserId(teacherUserId);
        t.setRole(role);
        t.setDisplayNameSnapshot(displayName);
        t.setColorKey(colorKey);
        return t;
    }

    private ErgPrincipal principal(List<String> permissions, List<String> deniedPermissions) {
        return new ErgPrincipal("user-1", "session-1", TENANT_ID, List.of("teacher"),
                permissions, deniedPermissions, List.of(), "user", "standard", Map.of());
    }

    private void stubLcmsWorkspaceEvents(List<String> schoolIds, List<CalendarEvent> events) {
        when(eventRepository.findLcmsWorkspaceEvents(
                eq(TENANT_ID), eq(schoolIds),
                any(OffsetDateTime.class), any(OffsetDateTime.class),
                eq(List.of("DRAFT", "PUBLISHED")),
                anyBoolean(), any(), anyBoolean(), any(),
                anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(events);
    }

    private void stubTeacherWorkspaceEvents(String teacherUserId, List<CalendarEvent> events) {
        when(eventRepository.findTeacherCalendarEventsFiltered(
                eq(TENANT_ID), eq(teacherUserId),
                any(OffsetDateTime.class), any(OffsetDateTime.class),
                eq(List.of("PUBLISHED")),
                anyBoolean(), any(), anyBoolean(), any(),
                anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(events);
    }

    // =========================================================================
    // 1. lcmsTeachingCalendarWorkspace
    // =========================================================================

    @Nested
    class LcmsTeachingCalendarWorkspaceTest {

        @Test
        void shouldFilterEventsBySchoolIdAndDateRange() {
            // Given
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            CalendarEvent eventB = event(EVENT_ID_B, SCHOOL_ID_B, CLASS_ID_B, SUBJECT_ID_B, LEVEL_ID_B,
                    BASE_TIME.plusDays(1), BASE_TIME.plusDays(1).plusHours(1), "PUBLISHED");

            stubLcmsWorkspaceEvents(List.of(SCHOOL_ID_A, SCHOOL_ID_B), List.of(eventA, eventB));

            when(eventTeacherRepository.findByTenantIdAndEventIdIn(eq(TENANT_ID), any()))
                    .thenReturn(List.of());

            // educationUnitRepository.findByIdInOrderByNameAsc is called by buildReferenceMaps for the
            // school IDs present in events. school-a and school-b will be queried.
            EducationUnit schoolA = new EducationUnit();
            schoolA.setId(SCHOOL_ID_A);
            schoolA.setName("School A");
            EducationUnit schoolB = new EducationUnit();
            schoolB.setId(SCHOOL_ID_B);
            schoolB.setName("School B");
            when(educationUnitRepository.findByIdInOrderByNameAsc(anyList()))
                    .thenReturn(List.of(schoolA, schoolB));

            // classroomRepository.findAllById will be called for class-a and class-b
            Classroom classA = new Classroom();
            classA.setId(CLASS_ID_A);
            classA.setName("Class A");
            Classroom classB = new Classroom();
            classB.setId(CLASS_ID_B);
            classB.setName("Class B");
            when(classroomRepository.findAllById(any()))
                    .thenReturn(List.of(classA, classB));

            // subjectRepository.findAllById will be called for subj-a and subj-b
            Subject subjectA = new Subject();
            subjectA.setId(SUBJECT_ID_A);
            subjectA.setName("Subject A");
            Subject subjectB = new Subject();
            subjectB.setId(SUBJECT_ID_B);
            subjectB.setName("Subject B");
            when(subjectRepository.findAllById(any()))
                    .thenReturn(List.of(subjectA, subjectB));

            // levelRepository.findAllById will be called for level-a and level-b
            Level levelA = new Level();
            levelA.setId(LEVEL_ID_A);
            levelA.setName("Level A");
            Level levelB = new Level();
            levelB.setId(LEVEL_ID_B);
            levelB.setName("Level B");
            when(levelRepository.findAllById(any()))
                    .thenReturn(List.of(levelA, levelB));

            // buildCatalog mocks
            when(educationUnitRepository.findByIdInOrderByNameAsc(List.of(SCHOOL_ID_A, SCHOOL_ID_B)))
                    .thenReturn(List.of(schoolA, schoolB));

            when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(TENANT_ID, List.of(SCHOOL_ID_A, SCHOOL_ID_B)))
                    .thenReturn(List.of(classA, classB));

            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE"))
                    .thenReturn(List.of(subjectA, subjectB));

            when(levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                    eq(TENANT_ID), anySet(), eq("ACTIVE")))
                    .thenReturn(List.of(levelA, levelB));

            ErgPrincipal adminPrincipal = principal(List.of("*"), List.of());

            // When
            CalendarWorkspaceResponse response = service.lcmsTeachingCalendarWorkspace(
                    TENANT_ID, List.of(SCHOOL_ID_A, SCHOOL_ID_B),
                    "2026-06-15", "2026-06-21", "SCHOOL",
                    null, null, null, null, null, adminPrincipal);

            // Then
            assertNotNull(response);
            assertEquals(TENANT_ID, response.tenantId());
            assertEquals("2026-06-15", response.from());
            assertEquals("2026-06-21", response.to());
            assertEquals("SCHOOL", response.viewMode());
            assertEquals(2, response.events().size());
            assertEquals(EVENT_ID_A, response.events().get(0).id());
            assertEquals(EVENT_ID_B, response.events().get(1).id());
            assertNotNull(response.catalog());
            assertNotNull(response.permissions());
            assertNotNull(response.filterOptions());
            assertNotNull(response.laneSummaries());
            assertNotNull(response.generatedAt());
        }

        @Test
        void shouldApplyTeacherFilter() {
            // Given
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            CalendarEvent eventB = event(EVENT_ID_B, SCHOOL_ID_A, CLASS_ID_B, SUBJECT_ID_B, LEVEL_ID_B,
                    BASE_TIME.plusDays(1), BASE_TIME.plusDays(1).plusHours(1), "PUBLISHED");

            CalendarEventTeacher assignA = assignment(EVENT_ID_A, TEACHER_USER_ID_A, "MAIN", "Teacher A", "blue");
            CalendarEventTeacher assignB = assignment(EVENT_ID_B, TEACHER_USER_ID_B, "MAIN", "Teacher B", "green");

            stubLcmsWorkspaceEvents(List.of(SCHOOL_ID_A), List.of(eventA));

            when(eventTeacherRepository.findByTenantIdAndEventIdIn(eq(TENANT_ID), any()))
                    .thenReturn(List.of(assignA));

            // buildReferenceMaps: educationUnitRepository for school-a
            EducationUnit schoolA = new EducationUnit();
            schoolA.setId(SCHOOL_ID_A);
            schoolA.setName("School A");
            when(educationUnitRepository.findByIdInOrderByNameAsc(anyList()))
                    .thenReturn(List.of(schoolA));

            Classroom classA = new Classroom();
            classA.setId(CLASS_ID_A);
            classA.setName("Class A");
            Classroom classB = new Classroom();
            classB.setId(CLASS_ID_B);
            classB.setName("Class B");
            when(classroomRepository.findAllById(any()))
                    .thenReturn(List.of(classA, classB));

            Subject subjectA = new Subject();
            subjectA.setId(SUBJECT_ID_A);
            subjectA.setName("Subject A");
            Subject subjectB = new Subject();
            subjectB.setId(SUBJECT_ID_B);
            subjectB.setName("Subject B");
            when(subjectRepository.findAllById(any()))
                    .thenReturn(List.of(subjectA, subjectB));

            when(levelRepository.findAllById(any()))
                    .thenReturn(List.of());

            // buildCatalog mocks (same schoolIds)
            when(educationUnitRepository.findByIdInOrderByNameAsc(List.of(SCHOOL_ID_A)))
                    .thenReturn(List.of(schoolA));
            when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(TENANT_ID, List.of(SCHOOL_ID_A)))
                    .thenReturn(List.of(classA, classB));
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE"))
                    .thenReturn(List.of(subjectA, subjectB));
            when(levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                    eq(TENANT_ID), anySet(), eq("ACTIVE")))
                    .thenReturn(List.of());

            ErgPrincipal adminPrincipal = principal(List.of("*"), List.of());

            // When — filter by teacher-a only
            CalendarWorkspaceResponse response = service.lcmsTeachingCalendarWorkspace(
                    TENANT_ID, List.of(SCHOOL_ID_A),
                    "2026-06-15", "2026-06-21", "SCHOOL",
                    List.of(TEACHER_USER_ID_A), null, null, null, null, adminPrincipal);

            // Then — only event-a should remain (teacher-a assigned to event-a)
            assertNotNull(response);
            assertEquals(1, response.events().size());
            assertEquals(EVENT_ID_A, response.events().get(0).id());
        }

        @Test
        void shouldApplyClassFilter() {
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            CalendarEvent eventB = event(EVENT_ID_B, SCHOOL_ID_A, CLASS_ID_B, SUBJECT_ID_B, LEVEL_ID_B,
                    BASE_TIME.plusDays(1), BASE_TIME.plusDays(1).plusHours(1), "PUBLISHED");

            stubLcmsWorkspaceEvents(List.of(SCHOOL_ID_A), List.of(eventA));

            when(eventTeacherRepository.findByTenantIdAndEventIdIn(eq(TENANT_ID), any()))
                    .thenReturn(List.of());

            // buildReferenceMaps
            EducationUnit schoolA = new EducationUnit();
            schoolA.setId(SCHOOL_ID_A);
            schoolA.setName("School A");
            when(educationUnitRepository.findByIdInOrderByNameAsc(anyList())).thenReturn(List.of(schoolA));

            Classroom classA = new Classroom();
            classA.setId(CLASS_ID_A);
            classA.setName("Class A");
            when(classroomRepository.findAllById(any())).thenReturn(List.of(classA));

            when(subjectRepository.findAllById(any())).thenReturn(List.of());

            // buildCatalog
            when(educationUnitRepository.findByIdInOrderByNameAsc(List.of(SCHOOL_ID_A))).thenReturn(List.of(schoolA));
            when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(TENANT_ID, List.of(SCHOOL_ID_A))).thenReturn(List.of(classA));
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE")).thenReturn(List.of());

            ErgPrincipal adminPrincipal = principal(List.of("*"), List.of());

            CalendarWorkspaceResponse response = service.lcmsTeachingCalendarWorkspace(
                    TENANT_ID, List.of(SCHOOL_ID_A),
                    "2026-06-15", "2026-06-21", "SCHOOL",
                    null, List.of(CLASS_ID_A), null, null, null, adminPrincipal);

            assertEquals(1, response.events().size());
            assertEquals(EVENT_ID_A, response.events().get(0).id());
        }

        @Test
        void shouldReturnEmptyResultsWhenNoEventsMatch() {
            stubLcmsWorkspaceEvents(List.of(SCHOOL_ID_A), List.of());

            // buildCatalog
            EducationUnit schoolA = new EducationUnit();
            schoolA.setId(SCHOOL_ID_A);
            schoolA.setName("School A");
            when(educationUnitRepository.findByIdInOrderByNameAsc(List.of(SCHOOL_ID_A))).thenReturn(List.of(schoolA));
            when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(TENANT_ID, List.of(SCHOOL_ID_A))).thenReturn(List.of());
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE")).thenReturn(List.of());

            ErgPrincipal adminPrincipal = principal(List.of("*"), List.of());

            CalendarWorkspaceResponse response = service.lcmsTeachingCalendarWorkspace(
                    TENANT_ID, List.of(SCHOOL_ID_A),
                    "2026-06-15", "2026-06-21", "SCHOOL",
                    null, null, null, null, null, adminPrincipal);

            assertNotNull(response);
            assertTrue(response.events().isEmpty());
            assertNotNull(response.filterOptions());
            assertTrue(response.laneSummaries().isEmpty());
            assertNotNull(response.catalog());
        }

        @Test
        void shouldUseDefaultStatusesWhenStatusListIsNull() {
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "DRAFT");

            stubLcmsWorkspaceEvents(List.of(SCHOOL_ID_A), List.of(eventA));

            when(eventTeacherRepository.findByTenantIdAndEventIdIn(eq(TENANT_ID), any())).thenReturn(List.of());

            EducationUnit schoolA = new EducationUnit();
            schoolA.setId(SCHOOL_ID_A);
            schoolA.setName("School A");
            when(educationUnitRepository.findByIdInOrderByNameAsc(anyList())).thenReturn(List.of(schoolA));
            when(classroomRepository.findAllById(any())).thenReturn(List.of());
            when(subjectRepository.findAllById(any())).thenReturn(List.of());

            when(educationUnitRepository.findByIdInOrderByNameAsc(List.of(SCHOOL_ID_A))).thenReturn(List.of(schoolA));
            when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(TENANT_ID, List.of(SCHOOL_ID_A))).thenReturn(List.of());
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE")).thenReturn(List.of());

            ErgPrincipal adminPrincipal = principal(List.of("*"), List.of());

            CalendarWorkspaceResponse response = service.lcmsTeachingCalendarWorkspace(
                    TENANT_ID, List.of(SCHOOL_ID_A),
                    "2026-06-15", "2026-06-21", "SCHOOL",
                    null, null, null, null, null, adminPrincipal);

            assertEquals(1, response.events().size());
            assertEquals("DRAFT", response.events().get(0).status());
        }
    }

    // =========================================================================
    // 2. teacherTeachingCalendarWorkspace
    // =========================================================================

    @Nested
    class TeacherTeachingCalendarWorkspaceTest {

        @Test
        void shouldReturnSelfScopedTeacherEvents() {
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            CalendarEventTeacher assignA = assignment(EVENT_ID_A, TEACHER_USER_ID_A, "MAIN", "Teacher A", "blue");

            stubTeacherWorkspaceEvents(TEACHER_USER_ID_A, List.of(eventA));

            when(eventTeacherRepository.findByTenantIdAndEventIdIn(eq(TENANT_ID), any()))
                    .thenReturn(List.of(assignA));

            // buildReferenceMaps
            EducationUnit schoolA = new EducationUnit();
            schoolA.setId(SCHOOL_ID_A);
            schoolA.setName("School A");
            when(educationUnitRepository.findByIdInOrderByNameAsc(anyList())).thenReturn(List.of(schoolA));
            when(classroomRepository.findAllById(any())).thenReturn(List.of());
            when(subjectRepository.findAllById(any())).thenReturn(List.of());

            // buildCatalog
            when(educationUnitRepository.findByIdInOrderByNameAsc(List.of(SCHOOL_ID_A))).thenReturn(List.of(schoolA));
            when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(TENANT_ID, List.of(SCHOOL_ID_A))).thenReturn(List.of());
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE")).thenReturn(List.of());

            ErgPrincipal teacherPrincipal = principal(List.of(), List.of());

            CalendarWorkspaceResponse response = service.teacherTeachingCalendarWorkspace(
                    TENANT_ID, TEACHER_USER_ID_A, "2026-06-15", "2026-06-21", "SCHOOL",
                    null, null, null, null, List.of("DRAFT", "PUBLISHED"), teacherPrincipal);

            assertNotNull(response);
            assertEquals(1, response.events().size());
            assertEquals(EVENT_ID_A, response.events().get(0).id());
        }

        @Test
        void shouldFilterTeacherEventsBySchoolId() {
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            CalendarEvent eventB = event(EVENT_ID_B, SCHOOL_ID_B, CLASS_ID_B, SUBJECT_ID_B, LEVEL_ID_B,
                    BASE_TIME.plusDays(1), BASE_TIME.plusDays(1).plusHours(1), "PUBLISHED");

            stubTeacherWorkspaceEvents(TEACHER_USER_ID_A, List.of(eventA));

            when(eventTeacherRepository.findByTenantIdAndEventIdIn(eq(TENANT_ID), any())).thenReturn(List.of());

            // buildReferenceMaps for school-a filter
            EducationUnit schoolA = new EducationUnit();
            schoolA.setId(SCHOOL_ID_A);
            schoolA.setName("School A");
            when(educationUnitRepository.findByIdInOrderByNameAsc(anyList())).thenReturn(List.of(schoolA));
            when(classroomRepository.findAllById(any())).thenReturn(List.of());
            when(subjectRepository.findAllById(any())).thenReturn(List.of());

            // buildCatalog
            when(educationUnitRepository.findByIdInOrderByNameAsc(List.of(SCHOOL_ID_A))).thenReturn(List.of(schoolA));
            when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(TENANT_ID, List.of(SCHOOL_ID_A))).thenReturn(List.of());
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE")).thenReturn(List.of());

            ErgPrincipal teacherPrincipal = principal(List.of(), List.of());

            CalendarWorkspaceResponse response = service.teacherTeachingCalendarWorkspace(
                    TENANT_ID, TEACHER_USER_ID_A, "2026-06-15", "2026-06-21", "SCHOOL",
                    List.of(SCHOOL_ID_A), null, null, null, null, teacherPrincipal);

            assertEquals(1, response.events().size());
            assertEquals(EVENT_ID_A, response.events().get(0).id());
            assertEquals(SCHOOL_ID_A, response.events().get(0).schoolId());
        }

        @Test
        void shouldReturnEmptyWhenTeacherHasNoEvents() {
            stubTeacherWorkspaceEvents(TEACHER_USER_ID_A, List.of());

            // buildCatalog with empty schoolIds fetches subjects
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE"))
                    .thenReturn(List.of());

            ErgPrincipal teacherPrincipal = principal(List.of(), List.of());

            CalendarWorkspaceResponse response = service.teacherTeachingCalendarWorkspace(
                    TENANT_ID, TEACHER_USER_ID_A, "2026-06-15", "2026-06-21", "SCHOOL",
                    null, null, null, null, null, teacherPrincipal);

            assertNotNull(response);
            assertTrue(response.events().isEmpty());
            assertTrue(response.laneSummaries().isEmpty());
        }
    }

    // =========================================================================
    // 3. buildEventResponse
    // =========================================================================

    @Nested
    class BuildEventResponseTest {

        @Test
        void shouldMapAllFieldsCorrectly() {
            CalendarEvent event = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");

            CalendarEventTeacher teacher = assignment(EVENT_ID_A, TEACHER_USER_ID_A, "MAIN",
                    "Teacher Alpha", "blue");

            CalendarReadService.ReferenceMaps refMaps = buildRefMapsWithNames(
                    SCHOOL_ID_A, "School Alpha",
                    CLASS_ID_A, "Class Alpha",
                    SUBJECT_ID_A, "Subject Alpha",
                    LEVEL_ID_A, "Level Alpha");

            CalendarEventResponse response = service.buildEventResponse(event, List.of(teacher), refMaps);

            assertNotNull(response);
            assertEquals(EVENT_ID_A, response.id());
            assertEquals("Test Event", response.title());
            assertEquals("2026-06-15", response.date());
            assertEquals("07:00", response.startTime());
            assertEquals("09:00", response.endTime());
            assertEquals(SCHOOL_ID_A, response.schoolId());
            assertEquals("School Alpha", response.schoolName());
            assertEquals(CLASS_ID_A, response.classId());
            assertEquals("Class Alpha", response.className());
            assertEquals(SUBJECT_ID_A, response.subjectId());
            assertEquals("Subject Alpha", response.subjectName());
            assertEquals(LEVEL_ID_A, response.levelId());
            assertEquals("Level Alpha", response.levelName());
            assertEquals("room-1", response.roomId());
            assertEquals("Room 101", response.roomName());
            assertEquals(1, response.periodStart());
            assertEquals(2, response.periodCount());
            assertEquals("PUBLISHED", response.status());

            assertEquals(1, response.teachers().size());
            CalendarTeacherAssignmentResponse teacherResp = response.teachers().get(0);
            assertEquals(TEACHER_USER_ID_A, teacherResp.userId());
            assertEquals("Teacher Alpha", teacherResp.displayName());
            assertEquals("MAIN", teacherResp.role());
            assertEquals("blue", teacherResp.colorKey());

            assertNotNull(response.colorGroup());
            // Subject ID "subj-a" should hash to one of the 8 color keys
            assertEquals("subject", response.colorGroup().kind());
        }

        @Test
        void shouldUseDisplayNameSnapshotWhenAvailable() {
            CalendarEvent event = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");

            // displayNameSnapshot set, so resolveTeacherName should NOT be called
            CalendarEventTeacher teacher = assignment(EVENT_ID_A, TEACHER_USER_ID_A, "MAIN",
                    "Snapshot Name", "red");

            CalendarReadService.ReferenceMaps refMaps = buildRefMapsWithNames(
                    SCHOOL_ID_A, "School A",
                    CLASS_ID_A, "Class A",
                    SUBJECT_ID_A, "Subject A",
                    LEVEL_ID_A, "Level A");

            CalendarEventResponse response = service.buildEventResponse(event, List.of(teacher), refMaps);

            assertEquals("Snapshot Name", response.teachers().get(0).displayName());
        }

        @Test
        void shouldResolveTeacherNameFromReferenceMapWhenSnapshotIsNull() {
            CalendarEvent event = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");

            CalendarEventTeacher teacher = assignment(EVENT_ID_A, TEACHER_USER_ID_A, "MAIN",
                    null, "green");

            // Build refMaps with userMap containing the teacher
            EducationUnit school = new EducationUnit();
            school.setId(SCHOOL_ID_A);
            school.setName("School A");
            Classroom cls = new Classroom();
            cls.setId(CLASS_ID_A);
            cls.setName("Class A");
            Subject subj = new Subject();
            subj.setId(SUBJECT_ID_A);
            subj.setName("Subject A");
            Level lvl = new Level();
            lvl.setId(LEVEL_ID_A);
            lvl.setName("Level A");
            User teacherUser = new User();
            teacherUser.setId(TEACHER_USER_ID_A);
            teacherUser.setFullName("Resolved Teacher");

            CalendarReadService.ReferenceMaps refMaps = mock(CalendarReadService.ReferenceMaps.class);
            when(refMaps.schoolMap()).thenReturn(Map.of(SCHOOL_ID_A, school));
            when(refMaps.classMap()).thenReturn(Map.of(CLASS_ID_A, cls));
            when(refMaps.subjectMap()).thenReturn(Map.of(SUBJECT_ID_A, subj));
            when(refMaps.levelMap()).thenReturn(Map.of(LEVEL_ID_A, lvl));
            when(refMaps.userMap()).thenReturn(Map.of(TEACHER_USER_ID_A, teacherUser));

            CalendarEventResponse response = service.buildEventResponse(event, List.of(teacher), refMaps);

            assertEquals("Resolved Teacher", response.teachers().get(0).displayName());
        }

        @Test
        void shouldHandleNullTimestamps() {
            CalendarEvent event = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            event.setStartAt(null);
            event.setEndAt(null);

            CalendarReadService.ReferenceMaps refMaps = buildRefMapsWithNames(
                    SCHOOL_ID_A, "School A",
                    CLASS_ID_A, "Class A",
                    SUBJECT_ID_A, "Subject A",
                    LEVEL_ID_A, "Level A");

            CalendarEventResponse response = service.buildEventResponse(event, List.of(), refMaps);

            assertNull(response.startAt());
            assertNull(response.endAt());
            assertNull(response.date());
            assertNull(response.startTime());
            assertNull(response.endTime());
            assertEquals(1, response.periodStart());
            assertEquals(2, response.periodCount());
        }

        @Test
        void shouldComputeColorGroupFromSubjectIdHash() {
            CalendarEvent event = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");

            CalendarReadService.ReferenceMaps refMaps = buildRefMapsWithNames(
                    SCHOOL_ID_A, "S", CLASS_ID_A, "C", SUBJECT_ID_A, "S", LEVEL_ID_A, "L");

            CalendarEventResponse response = service.buildEventResponse(event, List.of(), refMaps);

            assertNotNull(response.colorGroup());
            assertEquals("subject", response.colorGroup().kind());
            // The key is deterministic based on the hashCode of subjectId
            assertNotNull(response.colorGroup().key());
        }

        @Test
        void shouldReturnDefaultColorGroupWhenSubjectIdIsNull() {
            CalendarEvent event = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, null, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");

            CalendarReadService.ReferenceMaps refMaps = buildRefMapsWithNames(
                    SCHOOL_ID_A, "S", CLASS_ID_A, "C", "none", "none", LEVEL_ID_A, "L");

            CalendarEventResponse response = service.buildEventResponse(event, List.of(), refMaps);

            assertEquals("default", response.colorGroup().key());
            assertEquals("default", response.colorGroup().label());
            assertEquals("subject", response.colorGroup().kind());
        }
    }

    // =========================================================================
    // 4. buildFilterOptions
    // =========================================================================

    @Nested
    class BuildFilterOptionsTest {

        @Test
        void shouldComputeCountsAndLabels() {
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            CalendarEvent eventB = event(EVENT_ID_B, SCHOOL_ID_A, CLASS_ID_B, SUBJECT_ID_B, LEVEL_ID_B,
                    BASE_TIME.plusDays(1), BASE_TIME.plusDays(1).plusHours(1), "PUBLISHED");

            CalendarEventTeacher teacherA = assignment(EVENT_ID_A, TEACHER_USER_ID_A, "MAIN",
                    "Teacher A", "blue");
            CalendarEventTeacher teacherB1 = assignment(EVENT_ID_B, TEACHER_USER_ID_A, "MAIN",
                    "Teacher A", "blue");
            CalendarEventTeacher teacherB2 = assignment(EVENT_ID_B, TEACHER_USER_ID_B, "ASSISTANT",
                    "Teacher B", "green");

            Map<String, List<CalendarEventTeacher>> assignmentsByEvent = Map.of(
                    EVENT_ID_A, List.of(teacherA),
                    EVENT_ID_B, List.of(teacherB1, teacherB2));

            CalendarReadService.ReferenceMaps refMaps = buildRefMapsWithNames(
                    SCHOOL_ID_A, "School A",
                    CLASS_ID_A, "Class A",
                    SUBJECT_ID_A, "Subject A",
                    LEVEL_ID_A, "Level A");
            // Also add class B / subject B / level B
            Classroom classB = new Classroom();
            classB.setId(CLASS_ID_B);
            classB.setName("Class B");
            Subject subjectB = new Subject();
            subjectB.setId(SUBJECT_ID_B);
            subjectB.setName("Subject B");
            Level levelB = new Level();
            levelB.setId(LEVEL_ID_B);
            levelB.setName("Level B");
            User teacherUserA = new User();
            teacherUserA.setId(TEACHER_USER_ID_A);
            teacherUserA.setFullName("Teacher A");
            User teacherUserB = new User();
            teacherUserB.setId(TEACHER_USER_ID_B);
            teacherUserB.setFullName("Teacher B");

            // Build a new refMaps that includes all needed references
            CalendarReadService.ReferenceMaps fullRefMaps = new CalendarReadService.ReferenceMaps(
                    Map.of(SCHOOL_ID_A, createSchool(SCHOOL_ID_A, "School A")),
                    Map.of(CLASS_ID_A, createClass(CLASS_ID_A, "Class A"),
                           CLASS_ID_B, createClass(CLASS_ID_B, "Class B")),
                    Map.of(SUBJECT_ID_A, createSubject(SUBJECT_ID_A, "Subject A"),
                           SUBJECT_ID_B, createSubject(SUBJECT_ID_B, "Subject B")),
                    Map.of(LEVEL_ID_A, createLevel(LEVEL_ID_A, "Level A"),
                           LEVEL_ID_B, createLevel(LEVEL_ID_B, "Level B")),
                    Map.of(TEACHER_USER_ID_A, teacherUserA, TEACHER_USER_ID_B, teacherUserB),
                    Map.of(TEACHER_USER_ID_A, "blue", TEACHER_USER_ID_B, "green"));

            CalendarFilterOptionsResponse filterOptions = service.buildFilterOptions(
                    List.of(eventA, eventB), assignmentsByEvent, fullRefMaps, "SCHOOL");

            assertNotNull(filterOptions);

            // School counts: 2 events for school-a
            assertEquals(1, filterOptions.schools().size());
            assertEquals("School A", filterOptions.schools().get(0).label());
            assertEquals(2, filterOptions.schools().get(0).count());

            // Class counts: class-a=1, class-b=1
            assertEquals(2, filterOptions.classes().size());

            // Subject counts: subj-a=1, subj-b=1
            assertEquals(2, filterOptions.subjects().size());

            // Level counts: level-a=1, level-b=1
            assertEquals(2, filterOptions.levels().size());

            // Teacher counts: teacher-a=2 (across both events), teacher-b=1 (only event-b)
            Map<String, CalendarFilterOptionResponse> teacherMap = mapByIdFromFilter(filterOptions.teachers());
            assertEquals(2, teacherMap.size());
            assertTrue(teacherMap.containsKey(TEACHER_USER_ID_A));
            assertTrue(teacherMap.containsKey(TEACHER_USER_ID_B));
            assertEquals(2, teacherMap.get(TEACHER_USER_ID_A).count());
            assertEquals(1, teacherMap.get(TEACHER_USER_ID_B).count());
            assertEquals("blue", teacherMap.get(TEACHER_USER_ID_A).colorKey());
            assertEquals("green", teacherMap.get(TEACHER_USER_ID_B).colorKey());
        }

        @Test
        void shouldReturnEmptyOptionsForEmptyEvents() {
            CalendarFilterOptionsResponse filterOptions = service.buildFilterOptions(
                    List.of(), Map.of(), buildEmptyRefMaps(), "SCHOOL");

            assertNotNull(filterOptions);
            assertTrue(filterOptions.schools().isEmpty());
            assertTrue(filterOptions.classes().isEmpty());
            assertTrue(filterOptions.teachers().isEmpty());
            assertTrue(filterOptions.subjects().isEmpty());
            assertTrue(filterOptions.levels().isEmpty());
        }
    }

    // =========================================================================
    // 5. buildLaneSummaries
    // =========================================================================

    @Nested
    class BuildLaneSummariesTest {

        @Test
        void shouldBuildSchoolLaneSummaries() {
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            CalendarEvent eventB = event(EVENT_ID_B, SCHOOL_ID_B, CLASS_ID_B, SUBJECT_ID_B, LEVEL_ID_B,
                    BASE_TIME.plusDays(1), BASE_TIME.plusDays(1).plusHours(1), "PUBLISHED");

            CalendarReadService.ReferenceMaps refMaps = new CalendarReadService.ReferenceMaps(
                    Map.of(SCHOOL_ID_A, createSchool(SCHOOL_ID_A, "School Alpha"),
                           SCHOOL_ID_B, createSchool(SCHOOL_ID_B, "School Beta")),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

            List<CalendarLaneSummaryResponse> lanes = service.buildLaneSummaries(
                    List.of(eventA, eventB), Map.of(), refMaps, "SCHOOL");

            assertEquals(2, lanes.size());
            Map<String, CalendarLaneSummaryResponse> laneMap = mapById(lanes);
            assertEquals("School Alpha", laneMap.get(SCHOOL_ID_A).label());
            assertEquals(1, laneMap.get(SCHOOL_ID_A).count());
            assertEquals("School Beta", laneMap.get(SCHOOL_ID_B).label());
            assertEquals(1, laneMap.get(SCHOOL_ID_B).count());
            // SCHOOL mode does not set colorKey
            assertNull(laneMap.get(SCHOOL_ID_A).colorKey());
        }

        @Test
        void shouldBuildClassLaneSummaries() {
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            CalendarEvent eventB = event(EVENT_ID_B, SCHOOL_ID_A, CLASS_ID_B, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME.plusDays(1), BASE_TIME.plusDays(1).plusHours(1), "PUBLISHED");

            CalendarReadService.ReferenceMaps refMaps = new CalendarReadService.ReferenceMaps(
                    Map.of(),
                    Map.of(CLASS_ID_A, createClass(CLASS_ID_A, "Class Alpha"),
                           CLASS_ID_B, createClass(CLASS_ID_B, "Class Beta")),
                    Map.of(), Map.of(), Map.of(), Map.of());

            List<CalendarLaneSummaryResponse> lanes = service.buildLaneSummaries(
                    List.of(eventA, eventB), Map.of(), refMaps, "CLASS");

            assertEquals(2, lanes.size());
            Map<String, CalendarLaneSummaryResponse> laneMap = mapById(lanes);
            assertEquals("Class Alpha", laneMap.get(CLASS_ID_A).label());
            assertEquals(1, laneMap.get(CLASS_ID_A).count());
            assertEquals("Class Beta", laneMap.get(CLASS_ID_B).label());
            assertEquals(1, laneMap.get(CLASS_ID_B).count());
        }

        @Test
        void shouldBuildSubjectLaneSummaries() {
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            CalendarEvent eventB = event(EVENT_ID_B, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_B, LEVEL_ID_B,
                    BASE_TIME.plusDays(1), BASE_TIME.plusDays(1).plusHours(1), "PUBLISHED");

            CalendarReadService.ReferenceMaps refMaps = new CalendarReadService.ReferenceMaps(
                    Map.of(), Map.of(),
                    Map.of(SUBJECT_ID_A, createSubject(SUBJECT_ID_A, "Subject Alpha"),
                           SUBJECT_ID_B, createSubject(SUBJECT_ID_B, "Subject Beta")),
                    Map.of(), Map.of(), Map.of());

            List<CalendarLaneSummaryResponse> lanes = service.buildLaneSummaries(
                    List.of(eventA, eventB), Map.of(), refMaps, "SUBJECT");

            assertEquals(2, lanes.size());
            Map<String, CalendarLaneSummaryResponse> laneMap = mapById(lanes);
            assertEquals("Subject Alpha", laneMap.get(SUBJECT_ID_A).label());
            assertEquals(1, laneMap.get(SUBJECT_ID_A).count());
            assertEquals("Subject Beta", laneMap.get(SUBJECT_ID_B).label());
            assertEquals(1, laneMap.get(SUBJECT_ID_B).count());
        }

        @Test
        void shouldBuildTeacherLaneSummaries() {
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");
            CalendarEvent eventB = event(EVENT_ID_B, SCHOOL_ID_A, CLASS_ID_B, SUBJECT_ID_B, LEVEL_ID_B,
                    BASE_TIME.plusDays(1), BASE_TIME.plusDays(1).plusHours(1), "PUBLISHED");

            CalendarEventTeacher teacherA1 = assignment(EVENT_ID_A, TEACHER_USER_ID_A, "MAIN", null, "blue");
            CalendarEventTeacher teacherB = assignment(EVENT_ID_B, TEACHER_USER_ID_B, "MAIN", null, "green");

            Map<String, List<CalendarEventTeacher>> assignmentsByEvent = Map.of(
                    EVENT_ID_A, List.of(teacherA1),
                    EVENT_ID_B, List.of(teacherB));

            User teacherUserA = new User();
            teacherUserA.setId(TEACHER_USER_ID_A);
            teacherUserA.setFullName("Teacher Alpha");
            User teacherUserB = new User();
            teacherUserB.setId(TEACHER_USER_ID_B);
            teacherUserB.setFullName("Teacher Beta");

            CalendarReadService.ReferenceMaps refMaps = new CalendarReadService.ReferenceMaps(
                    Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(TEACHER_USER_ID_A, teacherUserA, TEACHER_USER_ID_B, teacherUserB),
                    Map.of(TEACHER_USER_ID_A, "blue", TEACHER_USER_ID_B, "green"));

            List<CalendarLaneSummaryResponse> lanes = service.buildLaneSummaries(
                    List.of(eventA, eventB), assignmentsByEvent, refMaps, "TEACHER");

            assertEquals(2, lanes.size());
            Map<String, CalendarLaneSummaryResponse> laneMap = mapById(lanes);
            assertEquals("Teacher Alpha", laneMap.get(TEACHER_USER_ID_A).label());
            assertEquals(1, laneMap.get(TEACHER_USER_ID_A).count());
            assertEquals("blue", laneMap.get(TEACHER_USER_ID_A).colorKey());
            assertEquals("Teacher Beta", laneMap.get(TEACHER_USER_ID_B).label());
            assertEquals(1, laneMap.get(TEACHER_USER_ID_B).count());
            assertEquals("green", laneMap.get(TEACHER_USER_ID_B).colorKey());
        }

        @Test
        void shouldDefaultToSchoolModeWhenViewModeIsNull() {
            CalendarEvent eventA = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");

            CalendarReadService.ReferenceMaps refMaps = new CalendarReadService.ReferenceMaps(
                    Map.of(SCHOOL_ID_A, createSchool(SCHOOL_ID_A, "School A")),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

            List<CalendarLaneSummaryResponse> lanes = service.buildLaneSummaries(
                    List.of(eventA), Map.of(), refMaps, null);

            assertEquals(1, lanes.size());
            assertEquals("School A", lanes.get(0).label());
        }

        @Test
        void shouldReturnEmptyListForNullEvents() {
            List<CalendarLaneSummaryResponse> lanes = service.buildLaneSummaries(
                    null, Map.of(), buildEmptyRefMaps(), "SCHOOL");
            assertTrue(lanes.isEmpty());
        }

        @Test
        void shouldReturnEmptyListForEmptyEvents() {
            List<CalendarLaneSummaryResponse> lanes = service.buildLaneSummaries(
                    List.of(), Map.of(), buildEmptyRefMaps(), "TEACHER");
            assertTrue(lanes.isEmpty());
        }
    }

    // =========================================================================
    // 6. buildCatalog
    // =========================================================================

    @Nested
    class BuildCatalogTest {

        @Test
        void catalogRepositoryReadsStayOnRequestTransactionThread() {
            Thread requestThread = Thread.currentThread();
            AtomicReference<Thread> repositoryThread = new AtomicReference<>();
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(
                    eq(TENANT_ID), eq("ACTIVE"), any(Pageable.class)))
                    .thenAnswer(invocation -> {
                        repositoryThread.set(Thread.currentThread());
                        return List.of();
                    });

            service.buildCatalog(TENANT_ID, List.of(), BASE_TIME, BASE_TIME.plusDays(7));

            assertSame(requestThread, repositoryThread.get(),
                    "JPA/JDBC reads must retain the request transaction and transaction-local tenant GUCs");
        }

        @Test
        void shouldRejectCatalogAboveFiveHundredOptions() {
            List<Subject> subjects = IntStream.range(0, 501)
                    .mapToObj(index -> createSubject("subject-" + index, "Subject " + index))
                    .toList();
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE"))
                    .thenReturn(subjects);

            BadRequestException failure = assertThrows(BadRequestException.class,
                    () -> service.buildCatalog(TENANT_ID, List.of(), BASE_TIME, BASE_TIME.plusDays(7)));
            assertTrue(failure.getMessage().contains("500"));
            assertTrue(failure.getMessage().contains("subjects"));
        }

        @Test
        void shouldAggregateAllReferenceData() {
            EducationUnit schoolA = createSchool(SCHOOL_ID_A, "School A");
            EducationUnit schoolB = createSchool(SCHOOL_ID_B, "School B");
            Classroom classA = createClass(CLASS_ID_A, "Class A");
            Classroom classB = createClass(CLASS_ID_B, "Class B");
            Subject subjectA = createSubject(SUBJECT_ID_A, "Subject A");
            Subject subjectB = createSubject(SUBJECT_ID_B, "Subject B");
            Level levelA = createLevel(LEVEL_ID_A, "Level A");
            Level levelB = createLevel(LEVEL_ID_B, "Level B");

            when(educationUnitRepository.findByIdInOrderByNameAsc(List.of(SCHOOL_ID_A, SCHOOL_ID_B)))
                    .thenReturn(List.of(schoolA, schoolB));
            when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(TENANT_ID, List.of(SCHOOL_ID_A, SCHOOL_ID_B)))
                    .thenReturn(List.of(classA, classB));
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE"))
                    .thenReturn(List.of(subjectA, subjectB));
            when(levelRepository.findByTenantIdAndSubjectIdInAndStatusIgnoreCaseOrderByOrderAscNameAsc(
                    eq(TENANT_ID), anySet(), eq("ACTIVE")))
                    .thenReturn(List.of(levelA, levelB));

            when(eventTeacherRepository.findTeacherColorKeysInRange(
                    eq(TENANT_ID), eq(List.of(SCHOOL_ID_A, SCHOOL_ID_B)),
                    eq(BASE_TIME), eq(BASE_TIME.plusDays(7)),
                    eq(List.of("DRAFT", "PUBLISHED"))))
                    .thenReturn(List.of(new CalendarTeacherColorProjection(TEACHER_USER_ID_A, "blue")));

            User teacherUser = new User();
            teacherUser.setId(TEACHER_USER_ID_A);
            teacherUser.setFullName("Teacher A");
            when(userRepository.findCalendarTeachersByTenantAndSchoolIds(TENANT_ID, List.of(SCHOOL_ID_A, SCHOOL_ID_B)))
                    .thenReturn(List.of(teacherUser));

            CalendarRoom room = new CalendarRoom();
            room.setId("room-1");
            room.setName("Room 101");
            room.setSchoolId(SCHOOL_ID_A);
            when(roomRepository.findByTenantIdAndSchoolIdInAndStatus(TENANT_ID, List.of(SCHOOL_ID_A, SCHOOL_ID_B), "ACTIVE"))
                    .thenReturn(List.of(room));

            CalendarCatalogResponse catalog = service.buildCatalog(
                    TENANT_ID, List.of(SCHOOL_ID_A, SCHOOL_ID_B), BASE_TIME, BASE_TIME.plusDays(7));

            assertNotNull(catalog);
            assertEquals(2, catalog.schools().size());
            assertEquals(2, catalog.classes().size());
            assertEquals(2, catalog.subjects().size());
            assertEquals(2, catalog.levels().size());
            assertEquals(1, catalog.rooms().size());
            assertEquals(1, catalog.teachers().size());
            assertEquals("Teacher A", catalog.teachers().get(0).label());
            assertEquals("blue", catalog.teachers().get(0).colorKey());
        }

        @Test
        void shouldReturnEmptyCatalogForNullSchoolIds() {
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE"))
                    .thenReturn(List.of());

            CalendarCatalogResponse catalog = service.buildCatalog(TENANT_ID, null, BASE_TIME, BASE_TIME.plusDays(7));
            assertNotNull(catalog);
            assertTrue(catalog.schools().isEmpty());
            assertTrue(catalog.classes().isEmpty());
            assertTrue(catalog.rooms().isEmpty());
            // Subjects and levels are still fetched because the call doesn't depend on schoolIds
        }

        @Test
        void shouldReturnEmptyCatalogForEmptySchoolIds() {
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE"))
                    .thenReturn(List.of());

            CalendarCatalogResponse catalog = service.buildCatalog(TENANT_ID, List.of(), BASE_TIME, BASE_TIME.plusDays(7));
            assertNotNull(catalog);
            assertTrue(catalog.schools().isEmpty());
            assertTrue(catalog.classes().isEmpty());
            assertTrue(catalog.rooms().isEmpty());
        }
    }

    // =========================================================================
    // 7. buildPermissions
    // =========================================================================

    @Nested
    class BuildPermissionsTest {

        @Test
        void shouldGrantFullAccessForWildcardPermission() {
            ErgPrincipal principal = principal(List.of("*"), List.of());
            CalendarPermissionsResponse permissions = service.buildPermissions(principal);

            assertTrue(permissions.canCreate());
            assertTrue(permissions.canUpdate());
            assertTrue(permissions.canDelete());
            assertTrue(permissions.canPublish());
            assertTrue(permissions.canViewAggregate());
        }

        @Test
        void shouldGrantManagePermissionsForLmsScheduleManage() {
            ErgPrincipal principal = principal(List.of("lms.schedule.manage"), List.of());
            CalendarPermissionsResponse permissions = service.buildPermissions(principal);

            assertTrue(permissions.canCreate());
            assertTrue(permissions.canUpdate());
            assertTrue(permissions.canDelete());
            assertTrue(permissions.canPublish());
            assertFalse(permissions.canViewAggregate());
        }

        @Test
        void shouldGrantViewAggregateForLmsScheduleRead() {
            ErgPrincipal principal = principal(List.of("lms.schedule.read"), List.of());
            CalendarPermissionsResponse permissions = service.buildPermissions(principal);

            assertFalse(permissions.canCreate());
            assertFalse(permissions.canUpdate());
            assertFalse(permissions.canDelete());
            assertFalse(permissions.canPublish());
            assertTrue(permissions.canViewAggregate());
        }

        @Test
        void shouldDenyWhenPermissionIsExplicitlyDenied() {
            ErgPrincipal principal = principal(
                    List.of("lms.schedule.manage", "lms.schedule.read"),
                    List.of("lms.schedule.manage"));
            CalendarPermissionsResponse permissions = service.buildPermissions(principal);

            assertFalse(permissions.canCreate());
            assertFalse(permissions.canUpdate());
            assertFalse(permissions.canDelete());
            assertFalse(permissions.canPublish());
            assertTrue(permissions.canViewAggregate());
        }

        @Test
        void shouldDenyAllWhenWildcardIsDenied() {
            ErgPrincipal principal = principal(
                    List.of("lms.schedule.manage", "lms.schedule.read"),
                    List.of("*"));
            CalendarPermissionsResponse permissions = service.buildPermissions(principal);

            assertFalse(permissions.canCreate());
            assertFalse(permissions.canUpdate());
            assertFalse(permissions.canDelete());
            assertFalse(permissions.canPublish());
            assertFalse(permissions.canViewAggregate());
        }

        @Test
        void shouldReturnAllFalseForNullPrincipal() {
            CalendarPermissionsResponse permissions = service.buildPermissions(null);

            assertFalse(permissions.canCreate());
            assertFalse(permissions.canUpdate());
            assertFalse(permissions.canDelete());
            assertFalse(permissions.canPublish());
            assertFalse(permissions.canViewAggregate());
        }

        @Test
        void shouldReturnAllFalseForPrincipalWithNoPermissions() {
            ErgPrincipal principal = principal(List.of(), List.of());
            CalendarPermissionsResponse permissions = service.buildPermissions(principal);

            assertFalse(permissions.canCreate());
            assertFalse(permissions.canUpdate());
            assertFalse(permissions.canDelete());
            assertFalse(permissions.canPublish());
            assertFalse(permissions.canViewAggregate());
        }
    }

    // =========================================================================
    // 8. Empty / edge cases
    // =========================================================================

    @Nested
    class EmptyResultsHandlingTest {

        @Test
        void lcmsWorkspaceWithNoEventsShouldReturnEmptyStructures() {
            stubLcmsWorkspaceEvents(List.of(SCHOOL_ID_A), List.of());

            // buildCatalog with school-a
            EducationUnit schoolA = createSchool(SCHOOL_ID_A, "School A");
            when(educationUnitRepository.findByIdInOrderByNameAsc(List.of(SCHOOL_ID_A))).thenReturn(List.of(schoolA));
            when(classroomRepository.findByTenantIdAndSchoolIdInOrderByNameAsc(TENANT_ID, List.of(SCHOOL_ID_A))).thenReturn(List.of());
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE")).thenReturn(List.of());

            ErgPrincipal principal = principal(List.of("*"), List.of());

            CalendarWorkspaceResponse response = service.lcmsTeachingCalendarWorkspace(
                    TENANT_ID, List.of(SCHOOL_ID_A),
                    "2026-06-15", "2026-06-21", "SCHOOL",
                    null, null, null, null, null, principal);

            assertTrue(response.events().isEmpty());
            assertTrue(response.laneSummaries().isEmpty());
            assertNotNull(response.filterOptions());
            assertTrue(response.filterOptions().schools().isEmpty());
            assertTrue(response.filterOptions().classes().isEmpty());
            assertTrue(response.filterOptions().teachers().isEmpty());
            assertTrue(response.filterOptions().subjects().isEmpty());
            assertTrue(response.filterOptions().levels().isEmpty());
        }

        @Test
        void teacherWorkspaceWithNoEventsShouldReturnEmptyStructures() {
            stubTeacherWorkspaceEvents(TEACHER_USER_ID_A, List.of());

            // buildCatalog is called with empty schoolIds; subjects query is always invoked
            when(subjectRepository.findByTenantIdAndStatusIgnoreCaseOrderByNameAsc(TENANT_ID, "ACTIVE"))
                    .thenReturn(List.of());

            ErgPrincipal principal = principal(List.of(), List.of());

            CalendarWorkspaceResponse response = service.teacherTeachingCalendarWorkspace(
                    TENANT_ID, TEACHER_USER_ID_A,
                    "2026-06-15", "2026-06-21", "SCHOOL",
                    null, null, null, null, null, principal);

            assertTrue(response.events().isEmpty());
            assertTrue(response.laneSummaries().isEmpty());
            assertNotNull(response.filterOptions());
            assertTrue(response.filterOptions().schools().isEmpty());
            assertNotNull(response.catalog());
        }

        @Test
        void buildEventResponseWithEmptyTeachersListShouldSucceed() {
            CalendarEvent event = event(EVENT_ID_A, SCHOOL_ID_A, CLASS_ID_A, SUBJECT_ID_A, LEVEL_ID_A,
                    BASE_TIME, BASE_TIME.plusHours(2), "PUBLISHED");

            CalendarReadService.ReferenceMaps refMaps = buildRefMapsWithNames(
                    SCHOOL_ID_A, "School A",
                    CLASS_ID_A, "Class A",
                    SUBJECT_ID_A, "Subject A",
                    LEVEL_ID_A, "Level A");

            CalendarEventResponse response = service.buildEventResponse(event, List.of(), refMaps);

            assertNotNull(response);
            assertTrue(response.teachers().isEmpty());
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private CalendarReadService.ReferenceMaps buildRefMapsWithNames(
            String schoolId, String schoolName,
            String classId, String className,
            String subjectId, String subjectName,
            String levelId, String levelName) {

        EducationUnit school = createSchool(schoolId, schoolName);
        Classroom cls = createClass(classId, className);
        Subject subj = createSubject(subjectId, subjectName);
        Level lvl = createLevel(levelId, levelName);

        return new CalendarReadService.ReferenceMaps(
                schoolId != null ? Map.of(schoolId, school) : Map.of(),
                classId != null ? Map.of(classId, cls) : Map.of(),
                subjectId != null ? Map.of(subjectId, subj) : Map.of(),
                levelId != null ? Map.of(levelId, lvl) : Map.of(),
                Map.of(), Map.of());
    }

    private CalendarReadService.ReferenceMaps buildEmptyRefMaps() {
        return new CalendarReadService.ReferenceMaps(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private EducationUnit createSchool(String id, String name) {
        EducationUnit school = new EducationUnit();
        school.setId(id);
        school.setName(name);
        return school;
    }

    private Classroom createClass(String id, String name) {
        Classroom cls = new Classroom();
        cls.setId(id);
        cls.setName(name);
        return cls;
    }

    private Subject createSubject(String id, String name) {
        Subject subj = new Subject();
        subj.setId(id);
        subj.setName(name);
        return subj;
    }

    private Level createLevel(String id, String name) {
        Level lvl = new Level();
        lvl.setId(id);
        lvl.setName(name);
        return lvl;
    }

    private static Map<String, CalendarLaneSummaryResponse> mapById(List<CalendarLaneSummaryResponse> items) {
        return items.stream().collect(java.util.stream.Collectors.toMap(
                CalendarLaneSummaryResponse::id,
                java.util.function.Function.identity()));
    }

    private static Map<String, CalendarFilterOptionResponse> mapByIdFromFilter(List<CalendarFilterOptionResponse> items) {
        return items.stream().collect(java.util.stream.Collectors.toMap(
                CalendarFilterOptionResponse::id,
                java.util.function.Function.identity()));
    }
}
