package com.erg.ergspringboot.service.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarConflictResponse;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.repository.calendar.CalendarConflictRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventTeacherRepository;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarConflictServiceTest {

    private static final String TENANT_A = "tenant-001";
    private static final String TENANT_B = "tenant-002";
    private static final String SCHOOL_ID = "school-001";
    private static final String CLASS_ID = "class-001";
    private static final String ROOM_NAME = "Room 101";

    @Mock
    private CalendarEventRepository eventRepository;
    @Mock
    private CalendarEventTeacherRepository eventTeacherRepository;
    @Mock
    private CalendarConflictRepository conflictRepository;

    private CalendarConflictService service;

    @BeforeEach
    void setUp() {
        service = new CalendarConflictService(eventRepository, eventTeacherRepository, conflictRepository);
    }

    // -------------------------------------------------------------------------
    // Helpers: create CalendarEvent and CalendarEventTeacher
    // -------------------------------------------------------------------------

    private CalendarEvent createEvent(String id, String tenantId, String schoolId,
                                      String classId, String roomName,
                                      OffsetDateTime startAt, OffsetDateTime endAt) {
        CalendarEvent event = new CalendarEvent();
        event.setId(id);
        event.setTenantId(tenantId);
        event.setSchoolId(schoolId);
        event.setAcademicClassId(classId);
        event.setRoomName(roomName);
        event.setStartAt(startAt);
        event.setEndAt(endAt);
        event.setStatus("ACTIVE");
        return event;
    }

    private CalendarEvent createEvent(String id, String tenantId, String classId,
                                      OffsetDateTime startAt, OffsetDateTime endAt) {
        return createEvent(id, tenantId, SCHOOL_ID, classId, ROOM_NAME, startAt, endAt);
    }

    private CalendarEventTeacher createTeacher(String eventId, String teacherUserId) {
        CalendarEventTeacher teacher = new CalendarEventTeacher();
        teacher.setEventId(eventId);
        teacher.setTeacherUserId(teacherUserId);
        return teacher;
    }

    // -------------------------------------------------------------------------
    // detectConflicts: empty / null inputs
    // -------------------------------------------------------------------------

    @Nested
    class EmptyOrNullInputTest {

        @Test
        void shouldReturnEmptyListWhenCandidateEventsIsNull() {
            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, null, List.of());

            assertNotNull(conflicts);
            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldReturnEmptyListWhenCandidateEventsIsEmpty() {
            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, List.of(), List.of());

            assertNotNull(conflicts);
            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldReturnEmptyListWhenCandidateEventsContainsOnlyNull() {
            List<CalendarEvent> events = Collections.singletonList(null);
            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertNotNull(conflicts);
            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldIgnoreNullTeacherEntries() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            List<CalendarEvent> events = List.of(
                    createEvent("evt-1", TENANT_A, CLASS_ID, start, end)
            );
            // null teacher entry should be skipped without NPE
            List<CalendarEventTeacher> teachers = Collections.singletonList(null);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            eq(TENANT_A), eq(CLASS_ID), eq(end), eq(start), eq("CANCELLED")))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            eq(TENANT_A), eq(SCHOOL_ID), eq(ROOM_NAME), eq(end), eq(start),
                            eq("CANCELLED")))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, teachers);

            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldIgnoreTeacherWithNullEventId() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            List<CalendarEvent> events = List.of(
                    createEvent("evt-1", TENANT_A, CLASS_ID, start, end)
            );
            CalendarEventTeacher badTeacher = new CalendarEventTeacher();
            badTeacher.setEventId(null);
            badTeacher.setTeacherUserId("teacher-1");

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            eq(TENANT_A), eq(CLASS_ID), eq(end), eq(start), eq("CANCELLED")))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            eq(TENANT_A), eq(SCHOOL_ID), eq(ROOM_NAME), eq(end), eq(start),
                            eq("CANCELLED")))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of(badTeacher));

            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldHandleEventWithNullIdGracefully() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent event = new CalendarEvent();
            event.setTenantId(TENANT_A);
            event.setSchoolId(SCHOOL_ID);
            event.setAcademicClassId(CLASS_ID);
            event.setRoomName(ROOM_NAME);
            event.setStartAt(start);
            event.setEndAt(end);
            // id not set -> null

            // Note: null event IDs are skipped (event == null || event.getId() == null) -> continue
            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, List.of(event), List.of());

            assertNotNull(conflicts);
            assertTrue(conflicts.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // detectConflicts: class overlap detection (DB-level)
    // -------------------------------------------------------------------------

    @Nested
    class ClassOverlapTest {

        @Test
        void shouldDetectClassTimeConflictWithExistingEvent() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            List<CalendarEvent> events = List.of(
                    createEvent("evt-new", TENANT_A, CLASS_ID, start, end)
            );
            CalendarEvent existingEvent = createEvent("evt-existing", TENANT_A, CLASS_ID, start, end);

            when(eventRepository.findClassTimeConflictsInWindow(
                    eq(TENANT_A), any(), eq(start), eq(end), eq("CANCELLED")))
                    .thenReturn(List.of(existingEvent));

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of(), Map.of("evt-new", "row-origin"));

            assertEquals(1, conflicts.size());
            CalendarConflictResponse conflict = conflicts.get(0);
            assertEquals("CLASS_TIME_CONFLICT", conflict.code());
            assertEquals("ERROR", conflict.severity());
            assertEquals("row-origin", conflict.clientRowId());
            assertEquals("evt-existing", conflict.existingEventId());
        }

        @Test
        void shouldNotDetectClassConflictWhenNoOverlapInDb() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            List<CalendarEvent> events = List.of(
                    createEvent("evt-new", TENANT_A, CLASS_ID, start, end)
            );

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            eq(TENANT_A), eq(CLASS_ID), eq(end), eq(start), eq("CANCELLED")))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertTrue(conflicts.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // detectConflicts: teacher overlap detection (DB-level)
    // -------------------------------------------------------------------------

    @Nested
    class TeacherOverlapTest {

        @Test
        void shouldDetectTeacherTimeConflictWithExistingEvent() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            List<CalendarEvent> events = List.of(
                    createEvent("evt-new", TENANT_A, CLASS_ID, start, end)
            );
            List<CalendarEventTeacher> teachers = List.of(
                    createTeacher("evt-new", "teacher-1")
            );

            CalendarEvent existingEvent = createEvent(
                    "evt-existing", TENANT_A, CLASS_ID, start, end
            );

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventTeacherRepository.findTeacherAssignmentsInWindow(
                    eq(TENANT_A), any(), eq(start), eq(end), eq("CANCELLED")))
                    .thenReturn(List.of(createTeacher("evt-existing", "teacher-1")));
            when(eventRepository.findByTenantIdAndIdInAndStatusNot(
                    eq(TENANT_A), any(), eq("CANCELLED")))
                    .thenReturn(List.of(existingEvent));

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, teachers);

            assertEquals(1, conflicts.size());
            CalendarConflictResponse conflict = conflicts.get(0);
            assertEquals("TEACHER_TIME_CONFLICT", conflict.code());
            assertEquals("ERROR", conflict.severity());
            assertEquals("evt-new", conflict.clientRowId());
            assertEquals("evt-existing", conflict.existingEventId());
        }

        @Test
        void shouldNotFlagSelfAsTeacherConflict() {
            // When findTeacherTimeConflicts returns the candidate event itself,
            // it should be skipped because IDs match
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent selfEvent = createEvent("evt-self", TENANT_A, CLASS_ID, start, end);
            List<CalendarEvent> events = List.of(selfEvent);
            List<CalendarEventTeacher> teachers = List.of(
                    createTeacher("evt-self", "teacher-1")
            );

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            // The DB returns the same "evt-self" as a conflict — but the service
            // should skip it because event.getId() == clientRowId
            when(eventTeacherRepository.findTeacherAssignmentsInWindow(
                    eq(TENANT_A), any(), eq(start), eq(end), eq("CANCELLED")))
                    .thenReturn(List.of(createTeacher("evt-self", "teacher-1")));
            when(eventRepository.findByTenantIdAndIdInAndStatusNot(
                    eq(TENANT_A), any(), eq("CANCELLED")))
                    .thenReturn(List.of(selfEvent));

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, teachers);

            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldDetectConflictsForMultipleTeachers() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            List<CalendarEvent> events = List.of(
                    createEvent("evt-new", TENANT_A, CLASS_ID, start, end)
            );
            List<CalendarEventTeacher> teachers = List.of(
                    createTeacher("evt-new", "teacher-1"),
                    createTeacher("evt-new", "teacher-2")
            );

            CalendarEvent existing1 = createEvent(
                    "evt-existing-1", TENANT_A, CLASS_ID, start, end
            );
            CalendarEvent existing2 = createEvent(
                    "evt-existing-2", TENANT_A, CLASS_ID, start, end
            );

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventTeacherRepository.findTeacherAssignmentsInWindow(
                    eq(TENANT_A), any(), eq(start), eq(end), eq("CANCELLED")))
                    .thenReturn(List.of(
                            createTeacher("evt-existing-1", "teacher-1"),
                            createTeacher("evt-existing-2", "teacher-2")));
            when(eventRepository.findByTenantIdAndIdInAndStatusNot(
                    eq(TENANT_A), any(), eq("CANCELLED")))
                    .thenReturn(List.of(existing1, existing2));

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, teachers);

            assertEquals(2, conflicts.size());
            Set<String> existingEventIds = Set.of(
                    conflicts.get(0).existingEventId(),
                    conflicts.get(1).existingEventId()
            );
            assertTrue(existingEventIds.contains("evt-existing-1"));
            assertTrue(existingEventIds.contains("evt-existing-2"));
        }
    }

    // -------------------------------------------------------------------------
    // detectConflicts: room overlap detection (DB-level)
    // -------------------------------------------------------------------------

    @Nested
    class RoomOverlapTest {

        @Test
        void shouldDetectRoomTimeConflictWithExistingEvent() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            List<CalendarEvent> events = List.of(
                    createEvent("evt-new", TENANT_A, SCHOOL_ID, CLASS_ID, ROOM_NAME, start, end)
            );
            CalendarEvent existingEvent = createEvent(
                    "evt-existing", TENANT_A, SCHOOL_ID, "other-class", ROOM_NAME, start, end);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository.findRoomTimeConflictsInWindow(
                    eq(TENANT_A), any(), eq(start), eq(end), eq("CANCELLED")))
                    .thenReturn(List.of(existingEvent));

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertEquals(1, conflicts.size());
            CalendarConflictResponse conflict = conflicts.get(0);
            assertEquals("ROOM_TIME_CONFLICT", conflict.code());
            assertEquals("WARNING", conflict.severity());
            assertEquals("evt-new", conflict.clientRowId());
        }

        @Test
        void shouldSkipRoomConflictWhenRoomNameIsNull() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent event = createEvent("evt-new", TENANT_A, CLASS_ID, start, end);
            event.setRoomName(null);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, List.of(event), List.of());

            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldSkipRoomConflictWhenRoomNameIsBlank() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent event = createEvent("evt-new", TENANT_A, CLASS_ID, start, end);
            event.setRoomName("   ");

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, List.of(event), List.of());

            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldDetectRoomConflictCaseInsensitively() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            // Room name in uppercase, DB query uses ignoreCase
            CalendarEvent event = createEvent("evt-new", TENANT_A, SCHOOL_ID, CLASS_ID,
                    "ROOM 101", start, end);
            CalendarEvent existingEvent = createEvent(
                    "evt-existing", TENANT_A, SCHOOL_ID, "other-class", "room 101", start, end);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository.findRoomTimeConflictsInWindow(
                    eq(TENANT_A), any(), eq(start), eq(end), eq("CANCELLED")))
                    .thenReturn(List.of(existingEvent));

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, List.of(event), List.of());

            assertEquals(1, conflicts.size());
            assertEquals("ROOM_TIME_CONFLICT", conflicts.get(0).code());
        }
    }

    // -------------------------------------------------------------------------
    // detectConflicts: cross-tenant isolation
    // -------------------------------------------------------------------------

    @Nested
    class CrossTenantIsolationTest {

        @Test
        void shouldNotDetectConflictsForDifferentTenant() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            // Events from tenant B, but we query with tenant A
            List<CalendarEvent> events = List.of(
                    createEvent("evt-b-1", TENANT_B, CLASS_ID, start, end)
            );

            // Even if DB has records for tenant B, querying with tenant A returns 0
            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            eq(TENANT_A), eq(CLASS_ID), eq(end), eq(start), eq("CANCELLED")))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            eq(TENANT_A), eq(SCHOOL_ID), eq(ROOM_NAME), eq(end), eq(start),
                            eq("CANCELLED")))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldNotDetectTeacherConflictForDifferentTenant() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            List<CalendarEvent> events = List.of(
                    createEvent("evt-a-1", TENANT_A, CLASS_ID, start, end)
            );
            List<CalendarEventTeacher> teachers = List.of(
                    createTeacher("evt-a-1", "teacher-1")
            );

            // The teacher has events in tenant B, not tenant A
            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository.findTeacherTimeConflicts(
                    eq(TENANT_A), eq("teacher-1"), eq(start), eq(end)))
                    .thenReturn(List.of());

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, teachers);

            assertTrue(conflicts.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // detectConflicts: cross-row (in-batch) conflict detection
    // -------------------------------------------------------------------------

    @Nested
    class CrossRowConflictTest {

        @Test
        void shouldDetectClassConflictBetweenTwoEventsInSameBatch() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent eventA = createEvent("evt-a", TENANT_A, CLASS_ID, start, end);
            CalendarEvent eventB = createEvent("evt-b", TENANT_A, CLASS_ID, start, end);
            eventA.setRoomName(null);
            eventB.setRoomName(null);
            List<CalendarEvent> events = List.of(eventA, eventB);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertEquals(1, conflicts.size());
            CalendarConflictResponse conflict = conflicts.get(0);
            assertEquals("CLASS_TIME_CONFLICT", conflict.code());
            assertEquals("ERROR", conflict.severity());
            assertEquals("evt-a", conflict.clientRowId());
            assertEquals("evt-b", conflict.existingEventId());
        }

        @Test
        void shouldDetectRoomConflictBetweenTwoEventsInSameBatch() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent eventA = createEvent("evt-a", TENANT_A, SCHOOL_ID, "class-a",
                    ROOM_NAME, start, end);
            CalendarEvent eventB = createEvent("evt-b", TENANT_A, SCHOOL_ID, "class-b",
                    ROOM_NAME, start, end);
            List<CalendarEvent> events = List.of(eventA, eventB);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            // One room conflict between the two events
            boolean hasRoomConflict = conflicts.stream()
                    .anyMatch(c -> "ROOM_TIME_CONFLICT".equals(c.code()));
            assertTrue(hasRoomConflict);
        }

        @Test
        void shouldDetectTeacherConflictBetweenTwoEventsInSameBatch() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent eventA = createEvent("evt-a", TENANT_A, "class-a", start, end);
            CalendarEvent eventB = createEvent("evt-b", TENANT_A, "class-b", start, end);
            eventA.setRoomName(null);
            eventB.setRoomName(null);
            List<CalendarEvent> events = List.of(eventA, eventB);
            List<CalendarEventTeacher> teachers = List.of(
                    createTeacher("evt-a", "teacher-1"),
                    createTeacher("evt-b", "teacher-1")
            );

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, teachers);

            boolean hasTeacherConflict = conflicts.stream()
                    .anyMatch(c -> "TEACHER_TIME_CONFLICT".equals(c.code())
                            && "Giao vien co lich trung nhau trong danh sach".equals(c.message()));
            assertTrue(hasTeacherConflict,
                    "Expected cross-row TEACHER_TIME_CONFLICT with in-batch message");
        }

        @Test
        void shouldNotDetectCrossRowConflictWhenEventsDoNotOverlap() {
            OffsetDateTime startA = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime endA = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            OffsetDateTime startB = OffsetDateTime.parse("2026-06-15T11:00:00+07:00");
            OffsetDateTime endB = OffsetDateTime.parse("2026-06-15T13:00:00+07:00");
            CalendarEvent eventA = createEvent("evt-a", TENANT_A, CLASS_ID, startA, endA);
            CalendarEvent eventB = createEvent("evt-b", TENANT_A, CLASS_ID, startB, endB);
            List<CalendarEvent> events = List.of(eventA, eventB);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldDetectMultipleConflictTypesForSamePair() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            // Same class AND same room -> both CLASS_TIME_CONFLICT and ROOM_TIME_CONFLICT
            CalendarEvent eventA = createEvent("evt-a", TENANT_A, SCHOOL_ID, CLASS_ID,
                    ROOM_NAME, start, end);
            CalendarEvent eventB = createEvent("evt-b", TENANT_A, SCHOOL_ID, CLASS_ID,
                    ROOM_NAME, start, end);
            List<CalendarEvent> events = List.of(eventA, eventB);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            // Should have both CLASS_TIME_CONFLICT and ROOM_TIME_CONFLICT
            assertEquals(2, conflicts.size());

            boolean hasClassConflict = conflicts.stream()
                    .anyMatch(c -> "CLASS_TIME_CONFLICT".equals(c.code()));
            boolean hasRoomConflict = conflicts.stream()
                    .anyMatch(c -> "ROOM_TIME_CONFLICT".equals(c.code()));

            assertTrue(hasClassConflict, "Expected CLASS_TIME_CONFLICT");
            assertTrue(hasRoomConflict, "Expected ROOM_TIME_CONFLICT");
        }

        @Test
        void shouldNotDetectCrossRowClassConflictWhenDifferentClassSameTime() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent eventA = createEvent("evt-a", TENANT_A, "class-a", start, end);
            CalendarEvent eventB = createEvent("evt-b", TENANT_A, "class-b", start, end);
            eventA.setRoomName(null);
            eventB.setRoomName(null);
            List<CalendarEvent> events = List.of(eventA, eventB);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldNotDetectCrossRowRoomConflictWhenDifferentRoomSameTime() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent eventA = createEvent("evt-a", TENANT_A, SCHOOL_ID, CLASS_ID,
                    "Room 101", start, end);
            CalendarEvent eventB = createEvent("evt-b", TENANT_A, SCHOOL_ID, "class-b",
                    "Room 102", start, end);
            List<CalendarEvent> events = List.of(eventA, eventB);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldHandleMoreThanTwoEventsInBatch() {
            OffsetDateTime startA = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime endA = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            OffsetDateTime startB = OffsetDateTime.parse("2026-06-15T11:00:00+07:00");
            OffsetDateTime endB = OffsetDateTime.parse("2026-06-15T13:00:00+07:00");

            // event-1 and event-3 overlap, event-2 does not overlap with either
            CalendarEvent event1 = createEvent("evt-1", TENANT_A, CLASS_ID, startA, endA);
            CalendarEvent event2 = createEvent("evt-2", TENANT_A, CLASS_ID, startB, endB);
            // event-3 overlaps with event-1 (same time)
            CalendarEvent event3 = createEvent("evt-3", TENANT_A, CLASS_ID, startA, endA);
            event1.setRoomName(null);
            event2.setRoomName(null);
            event3.setRoomName(null);
            List<CalendarEvent> events = List.of(event1, event2, event3);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            // Should have exactly one conflict: evt-1 <-> evt-3 (evt-2 is non-overlapping)
            assertEquals(1, conflicts.size());
            assertEquals("CLASS_TIME_CONFLICT", conflicts.get(0).code());
        }
    }

    // -------------------------------------------------------------------------
    // detectConflicts: isOverlapping boundary conditions
    // -------------------------------------------------------------------------

    @Nested
    class OverlapBoundaryTest {

        @Test
        void shouldNotDetectConflictWhenOneEventEndsExactlyWhenAnotherStarts() {
            OffsetDateTime startA = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime endA = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            OffsetDateTime startB = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            OffsetDateTime endB = OffsetDateTime.parse("2026-06-15T12:00:00+07:00");
            // endA == startB: a.endAt is NOT after b.startAt -> no overlap
            CalendarEvent eventA = createEvent("evt-a", TENANT_A, CLASS_ID, startA, endA);
            CalendarEvent eventB = createEvent("evt-b", TENANT_A, CLASS_ID, startB, endB);
            List<CalendarEvent> events = List.of(eventA, eventB);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertTrue(conflicts.isEmpty());
        }

        @Test
        void shouldDetectOverlapWhenOneEventIsInsideAnother() {
            OffsetDateTime outerStart = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime outerEnd = OffsetDateTime.parse("2026-06-15T12:00:00+07:00");
            OffsetDateTime innerStart = OffsetDateTime.parse("2026-06-15T09:00:00+07:00");
            OffsetDateTime innerEnd = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent outer = createEvent("evt-outer", TENANT_A, CLASS_ID, outerStart, outerEnd);
            CalendarEvent inner = createEvent("evt-inner", TENANT_A, CLASS_ID, innerStart, innerEnd);
            outer.setRoomName(null);
            inner.setRoomName(null);
            List<CalendarEvent> events = List.of(outer, inner);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertEquals(1, conflicts.size());
        }

        @Test
        void shouldNotOverlapWhenEventHasNullStartOrEnd() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent normal = createEvent("evt-normal", TENANT_A, CLASS_ID, start, end);
            CalendarEvent noStart = createEvent("evt-nostart", TENANT_A, CLASS_ID, null, end);
            CalendarEvent noEnd = createEvent("evt-noend", TENANT_A, CLASS_ID, start, null);
            List<CalendarEvent> events = List.of(normal, noStart, noEnd);

            when(eventRepository
                    .countByTenantIdAndAcademicClassIdAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);
            when(eventRepository
                    .countByTenantIdAndSchoolIdAndRoomNameIgnoreCaseAndStartAtLessThanAndEndAtGreaterThanAndStatusNot(
                            anyString(), anyString(), anyString(), any(), any(), anyString()))
                    .thenReturn(0L);

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            assertTrue(conflicts.isEmpty(),
                    "Events with null start/end should not produce overlap conflicts");
        }
    }

    // -------------------------------------------------------------------------
    // detectConflicts: mixed scenario (DB + cross-row combined)
    // -------------------------------------------------------------------------

    @Nested
    class MixedDetectionTest {

        @Test
        void shouldCombineDbAndCrossRowConflicts() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");

            // Two events in the same batch, same class, overlapping
            CalendarEvent eventA = createEvent("evt-a", TENANT_A, CLASS_ID, start, end);
            CalendarEvent eventB = createEvent("evt-b", TENANT_A, CLASS_ID, start, end);
            eventA.setRoomName(null);
            eventB.setRoomName(null);
            CalendarEvent existingEvent = createEvent("evt-existing", TENANT_A, CLASS_ID, start, end);
            List<CalendarEvent> events = List.of(eventA, eventB);

            when(eventRepository.findClassTimeConflictsInWindow(
                    eq(TENANT_A), any(), eq(start), eq(end), eq("CANCELLED")))
                    .thenReturn(List.of(existingEvent));

            List<CalendarConflictResponse> conflicts =
                    service.detectConflicts(TENANT_A, events, List.of());

            // Expect 3 conflicts:
            // 1-2. DB-level class conflicts for both overlapping candidate rows
            // 3. Cross-row class conflict for evt-a vs evt-b
            assertEquals(3, conflicts.size(), "Should detect both DB and cross-row conflicts");
        }
    }

    // -------------------------------------------------------------------------
    // resolveConflictsByDeletion
    // -------------------------------------------------------------------------

    @Nested
    class ResolveConflictsByDeletionTest {

        @Test
        void shouldReturnEmptyWhenConflictsIsNull() {
            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, null, List.of("school-001"));

            assertNotNull(result);
            assertTrue(result.resolvedConflicts().isEmpty());
            assertTrue(result.eventIdsToDelete().isEmpty());
        }

        @Test
        void shouldReturnEmptyWhenConflictsIsEmpty() {
            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, List.of(), List.of("school-001"));

            assertNotNull(result);
            assertTrue(result.resolvedConflicts().isEmpty());
            assertTrue(result.eventIdsToDelete().isEmpty());
        }

        @Test
        void shouldOnlyProcessTeacherTimeConflicts() {
            List<CalendarConflictResponse> conflicts = List.of(
                    new CalendarConflictResponse("CLASS_TIME_CONFLICT", "ERROR",
                            "evt-new", "evt-existing", "Class conflict"),
                    new CalendarConflictResponse("ROOM_TIME_CONFLICT", "WARNING",
                            "evt-new", "evt-existing-room", "Room conflict")
            );

            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, conflicts, List.of("school-001"));

            assertTrue(result.resolvedConflicts().isEmpty());
            assertTrue(result.eventIdsToDelete().isEmpty());
        }

        @Test
        void shouldSkipConflictWhenExistingEventIdIsNull() {
            List<CalendarConflictResponse> conflicts = List.of(
                    new CalendarConflictResponse("TEACHER_TIME_CONFLICT", "ERROR",
                            "evt-new", null, "Teacher conflict")
            );

            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, conflicts, List.of("school-001"));

            assertTrue(result.resolvedConflicts().isEmpty());
            assertTrue(result.eventIdsToDelete().isEmpty());
        }

        @Test
        void shouldSkipConflictWhenExistingEventIdIsBlank() {
            List<CalendarConflictResponse> conflicts = List.of(
                    new CalendarConflictResponse("TEACHER_TIME_CONFLICT", "ERROR",
                            "evt-new", "   ", "Teacher conflict")
            );

            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, conflicts, List.of("school-001"));

            assertTrue(result.resolvedConflicts().isEmpty());
            assertTrue(result.eventIdsToDelete().isEmpty());
        }

        @Test
        void shouldResolveTeacherTimeConflictWhenEventExistsAndSchoolIsManaged() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent existingEvent = createEvent("evt-existing", TENANT_A, CLASS_ID, start, end);

            when(eventRepository.findByTenantIdAndIdIn(TENANT_A, List.of("evt-existing")))
                    .thenReturn(List.of(existingEvent));

            List<CalendarConflictResponse> conflicts = List.of(
                    new CalendarConflictResponse("TEACHER_TIME_CONFLICT", "ERROR",
                            "evt-new", "evt-existing", "Teacher conflict")
            );

            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, conflicts, List.of(SCHOOL_ID));

            assertEquals(1, result.resolvedConflicts().size());
            assertEquals(1, result.eventIdsToDelete().size());
            assertTrue(result.eventIdsToDelete().contains("evt-existing"));
        }

        @Test
        void shouldSkipWhenExistingEventNotFoundInDb() {
            when(eventRepository.findByTenantIdAndIdIn(TENANT_A, List.of("evt-nonexistent")))
                    .thenReturn(List.of());

            List<CalendarConflictResponse> conflicts = List.of(
                    new CalendarConflictResponse("TEACHER_TIME_CONFLICT", "ERROR",
                            "evt-new", "evt-nonexistent", "Teacher conflict")
            );

            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, conflicts, List.of(SCHOOL_ID));

            assertTrue(result.resolvedConflicts().isEmpty());
            assertTrue(result.eventIdsToDelete().isEmpty());
        }

        @Test
        void shouldSkipWhenEventSchoolIsNotInManagedSchoolIds() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent existingEvent = createEvent("evt-existing", TENANT_A, CLASS_ID, start, end);
            existingEvent.setSchoolId("school-other");

            when(eventRepository.findByTenantIdAndIdIn(TENANT_A, List.of("evt-existing")))
                    .thenReturn(List.of(existingEvent));

            List<CalendarConflictResponse> conflicts = List.of(
                    new CalendarConflictResponse("TEACHER_TIME_CONFLICT", "ERROR",
                            "evt-new", "evt-existing", "Teacher conflict")
            );

            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, conflicts, List.of(SCHOOL_ID));

            assertTrue(result.resolvedConflicts().isEmpty());
            assertTrue(result.eventIdsToDelete().isEmpty());
        }

        @Test
        void shouldSkipWhenManagedSchoolIdsIsNull() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent existingEvent = createEvent("evt-existing", TENANT_A, CLASS_ID, start, end);

            when(eventRepository.findByTenantIdAndIdIn(TENANT_A, List.of("evt-existing")))
                    .thenReturn(List.of(existingEvent));

            List<CalendarConflictResponse> conflicts = List.of(
                    new CalendarConflictResponse("TEACHER_TIME_CONFLICT", "ERROR",
                            "evt-new", "evt-existing", "Teacher conflict")
            );

            // managedSchoolIds = null -> the contains check is skipped
            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, conflicts, null);

            assertEquals(1, result.resolvedConflicts().size());
            assertEquals(1, result.eventIdsToDelete().size());
            assertTrue(result.eventIdsToDelete().contains("evt-existing"));
        }

        @Test
        void shouldResolveMultipleTeacherConflicts() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent existing1 = createEvent("evt-existing-1", TENANT_A, CLASS_ID, start, end);
            CalendarEvent existing2 = createEvent("evt-existing-2", TENANT_A, CLASS_ID, start, end);

            when(eventRepository.findByTenantIdAndIdIn(TENANT_A, List.of("evt-existing-1", "evt-existing-2")))
                    .thenReturn(List.of(existing1, existing2));

            List<CalendarConflictResponse> conflicts = List.of(
                    new CalendarConflictResponse("TEACHER_TIME_CONFLICT", "ERROR",
                            "evt-new-1", "evt-existing-1", "Conflict 1"),
                    new CalendarConflictResponse("TEACHER_TIME_CONFLICT", "ERROR",
                            "evt-new-2", "evt-existing-2", "Conflict 2"),
                    // Non-teacher conflict should be skipped
                    new CalendarConflictResponse("CLASS_TIME_CONFLICT", "ERROR",
                            "evt-new-3", "evt-existing-3", "Class conflict")
            );

            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, conflicts, List.of(SCHOOL_ID));

            assertEquals(2, result.resolvedConflicts().size());
            assertEquals(2, result.eventIdsToDelete().size());
            assertTrue(result.eventIdsToDelete().contains("evt-existing-1"));
            assertTrue(result.eventIdsToDelete().contains("evt-existing-2"));
        }

        @Test
        void shouldLoadExistingEventsInOneTenantScopedBatch() {
            OffsetDateTime start = OffsetDateTime.parse("2026-06-15T08:00:00+07:00");
            OffsetDateTime end = OffsetDateTime.parse("2026-06-15T10:00:00+07:00");
            CalendarEvent existing1 = createEvent("evt-existing-1", TENANT_A, CLASS_ID, start, end);
            CalendarEvent existing2 = createEvent("evt-existing-2", TENANT_A, CLASS_ID, start, end);
            when(eventRepository.findByTenantIdAndIdIn(eq(TENANT_A), eq(List.of("evt-existing-1", "evt-existing-2"))))
                    .thenReturn(List.of(existing1, existing2));

            List<CalendarConflictResponse> conflicts = List.of(
                    new CalendarConflictResponse("TEACHER_TIME_CONFLICT", "ERROR",
                            "evt-new-1", "evt-existing-1", "Conflict 1"),
                    new CalendarConflictResponse("TEACHER_TIME_CONFLICT", "ERROR",
                            "evt-new-2", "evt-existing-2", "Conflict 2")
            );

            CalendarConflictService.ResolveResult result =
                    service.resolveConflictsByDeletion(TENANT_A, conflicts, List.of(SCHOOL_ID));

            assertEquals(2, result.resolvedConflicts().size());
            verify(eventRepository).findByTenantIdAndIdIn(eq(TENANT_A), eq(List.of("evt-existing-1", "evt-existing-2")));
            verify(eventRepository, never()).findById(anyString());
        }
    }
}
