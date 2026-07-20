package com.erg.ergspringboot.service.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRowMessageResponse;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.service.calendar.CalendarExpansionService.ReferenceData;
import com.erg.ergspringboot.service.calendar.CalendarValidationService.RowValidationResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CalendarValidationServiceTest {

    private CalendarValidationService service;

    private Classroom classInSchool;
    private Classroom classOtherSchool;
    private Subject math;
    private Level mathLevelBeginner;
    private Level levelOtherSubject;
    private User teacherMain;
    private User teacherAssistant;
    private ReferenceData refData;

    @BeforeEach
    void setUp() {
        service = new CalendarValidationService();

        classInSchool = new Classroom();
        classInSchool.setId("class-a");
        classInSchool.setName("Class 3A");
        classInSchool.setSchoolId("school-001");

        classOtherSchool = new Classroom();
        classOtherSchool.setId("class-b");
        classOtherSchool.setName("Class 3B");
        classOtherSchool.setSchoolId("school-999");

        math = new Subject();
        math.setId("subj-math");
        math.setName("Mathematics");

        mathLevelBeginner = new Level();
        mathLevelBeginner.setId("level-beginner");
        mathLevelBeginner.setSubjectId("subj-math");
        mathLevelBeginner.setName("Beginner");

        levelOtherSubject = new Level();
        levelOtherSubject.setId("level-other");
        levelOtherSubject.setSubjectId("subj-physics");
        levelOtherSubject.setName("Physics Level");

        teacherMain = new User();
        teacherMain.setId("teacher-main-1");
        teacherMain.setFullName("Nguyen Van A");

        teacherAssistant = new User();
        teacherAssistant.setId("teacher-assistant-1");
        teacherAssistant.setFullName("Tran Thi B");

        refData = new ReferenceData(
                Map.of("class-a", classInSchool, "class-b", classOtherSchool),
                Map.of("subj-math", math),
                Map.of("level-beginner", mathLevelBeginner, "level-other", levelOtherSubject),
                Map.of("teacher-main-1", teacherMain, "teacher-assistant-1", teacherAssistant),
                Map.of()
        );
    }

    // -------------------------------------------------------------------------
    // Helper: build a minimal valid row
    // -------------------------------------------------------------------------

    private CalendarBulkRowRequest validRow() {
        return new CalendarBulkRowRequest(
                "row-1",
                List.of("class-a"),
                2,                  // Tuesday
                "MORNING",
                1,                  // periodStart
                3,                  // periodCount
                "08:00",
                "10:30",
                "subj-math",
                "level-beginner",
                "room-101",
                "Room 101",
                List.of("teacher-main-1"),
                List.of("teacher-assistant-1"),
                "Some note"
        );
    }

    private CalendarBulkCreateRequest requestWithRows(CalendarBulkRowRequest... rows) {
        return new CalendarBulkCreateRequest(
                "tenant-001",
                "idempotency-key-1",
                "school-001",
                "2025-2026",
                "2026-06-15",
                1,
                null,
                false,
                null,
                List.of(rows)
        );
    }

    // -------------------------------------------------------------------------
    // Helper: find error with given code in a result
    // -------------------------------------------------------------------------

    private static CalendarRowMessageResponse findError(RowValidationResult result, String code) {
        return result.errors().stream()
                .filter(e -> e.code().equals(code))
                .findFirst()
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Nested
    class HappyPathTest {

        @Test
        void shouldReturnValidWithNoErrorsForCompleteRow() {
            CalendarBulkRowRequest row = validRow();
            CalendarBulkCreateRequest request = requestWithRows(row);

            List<RowValidationResult> results = service.validateRows(request, refData);

            assertEquals(1, results.size());
            RowValidationResult result = results.get(0);
            assertTrue(result.valid());
            assertEquals("row-1", result.clientRowId());
            assertTrue(result.errors().isEmpty());
            assertTrue(result.warnings().isEmpty());
        }

        @Test
        void shouldReturnValidWhenLevelIsNull() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1",
                    List.of("class-a"),
                    2,
                    "MORNING",
                    1,
                    3,
                    "08:00",
                    "10:30",
                    "subj-math",
                    null,       // levelId = null
                    "room-101",
                    "Room 101",
                    List.of("teacher-main-1"),
                    List.of(),
                    null
            );
            CalendarBulkCreateRequest request = requestWithRows(row);

            List<RowValidationResult> results = service.validateRows(request, refData);

            assertEquals(1, results.size());
            assertTrue(results.get(0).valid());
        }

        @Test
        void shouldReturnValidWhenLevelIsBlank() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1",
                    List.of("class-a"),
                    2,
                    "MORNING",
                    1,
                    3,
                    "08:00",
                    "10:30",
                    "subj-math",
                    "",         // levelId = blank
                    "room-101",
                    "Room 101",
                    List.of("teacher-main-1"),
                    List.of(),
                    null
            );
            CalendarBulkCreateRequest request = requestWithRows(row);

            List<RowValidationResult> results = service.validateRows(request, refData);

            assertEquals(1, results.size());
            assertTrue(results.get(0).valid());
        }

        @Test
        void shouldReturnValidWhenAssistantTeacherIdsAreNull() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1",
                    List.of("class-a"),
                    2,
                    "MORNING",
                    1,
                    3,
                    "08:00",
                    "10:30",
                    "subj-math",
                    null,
                    null,
                    null,
                    List.of("teacher-main-1"),
                    null,       // assistantTeacherIds = null
                    null
            );
            CalendarBulkCreateRequest request = requestWithRows(row);

            List<RowValidationResult> results = service.validateRows(request, refData);

            assertEquals(1, results.size());
            assertTrue(results.get(0).valid());
        }

        @Test
        void shouldHandleMinimalBoundaryPeriodCount() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1",
                    List.of("class-a"),
                    2,
                    "MORNING",
                    1,
                    1,          // periodCount = 1 (minimum valid)
                    "08:00",
                    "08:45",
                    "subj-math",
                    null,
                    null,
                    null,
                    List.of("teacher-main-1"),
                    List.of(),
                    null
            );
            CalendarBulkCreateRequest request = requestWithRows(row);

            List<RowValidationResult> results = service.validateRows(request, refData);

            assertEquals(1, results.size());
            assertTrue(results.get(0).valid());
        }

        @Test
        void shouldHandleMaxBoundaryPeriodCount() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1",
                    List.of("class-a"),
                    2,
                    "MORNING",
                    1,
                    10,         // periodCount = 10 (maximum valid)
                    "08:00",
                    "17:00",
                    "subj-math",
                    null,
                    null,
                    null,
                    List.of("teacher-main-1"),
                    List.of(),
                    null
            );
            CalendarBulkCreateRequest request = requestWithRows(row);

            List<RowValidationResult> results = service.validateRows(request, refData);

            assertEquals(1, results.size());
            assertTrue(results.get(0).valid());
        }
    }

    // -------------------------------------------------------------------------
    // CLASS_REQUIRED
    // -------------------------------------------------------------------------

    @Nested
    class ClassRequiredTest {

        @Test
        void shouldFailWhenClassIdsIsNull() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", null, 2, "MORNING", 1, 3, "08:00", "10:30",
                    "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "CLASS_REQUIRED");
            assertErrorPresent(results.get(0), err, "classIds");
        }

        @Test
        void shouldFailWhenClassIdsIsEmpty() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of(), 2, "MORNING", 1, 3, "08:00", "10:30",
                    "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "CLASS_REQUIRED");
            assertErrorPresent(results.get(0), err, "classIds");
        }
    }

    // -------------------------------------------------------------------------
    // CLASS_NOT_FOUND
    // -------------------------------------------------------------------------

    @Nested
    class ClassNotFoundTest {

        @Test
        void shouldFailWhenClassIdDoesNotExistInReferenceData() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-unknown"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "CLASS_NOT_FOUND");
            assertErrorPresent(results.get(0), err, "classIds");
            assertTrue(err.message().contains("class-unknown"));
        }

        @Test
        void shouldReportNotFoundForEachUnknownClassId() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-unknown-a", "class-unknown-b"),
                    2, "MORNING", 1, 3, "08:00", "10:30",
                    "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            long notFoundCount = results.get(0).errors().stream()
                    .filter(e -> "CLASS_NOT_FOUND".equals(e.code()))
                    .count();
            assertEquals(2, notFoundCount);
        }

        @Test
        void shouldReportClassNotFoundBeforeClassNotInSchool() {
            // class id exists in map but belongs to wrong school -> CLASS_NOT_IN_SCHOOL, not CLASS_NOT_FOUND
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-b"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            assertTrue(findError(results.get(0), "CLASS_NOT_FOUND") == null,
                    "class-b exists in map, so CLASS_NOT_FOUND should not fire");
            CalendarRowMessageResponse err = findError(results.get(0), "CLASS_NOT_IN_SCHOOL");
            assertErrorPresent(results.get(0), err, "classIds");
        }
    }

    // -------------------------------------------------------------------------
    // CLASS_NOT_IN_SCHOOL
    // -------------------------------------------------------------------------

    @Nested
    class ClassNotInSchoolTest {

        @Test
        void shouldFailWhenClassDoesNotBelongToRequestSchoolId() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-b"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            CalendarBulkCreateRequest request = new CalendarBulkCreateRequest(
                    "tenant-001", "key-1", "school-001", "2025-2026",
                    "2026-06-15", 1, null, false, null, List.of(row)
            );
            List<RowValidationResult> results = service.validateRows(request, refData);

            CalendarRowMessageResponse err = findError(results.get(0), "CLASS_NOT_IN_SCHOOL");
            assertErrorPresent(results.get(0), err, "classIds");
            assertTrue(err.message().contains("Class 3B"));
        }
    }

    // -------------------------------------------------------------------------
    // MAIN_TEACHER_REQUIRED
    // -------------------------------------------------------------------------

    @Nested
    class MainTeacherRequiredTest {

        @Test
        void shouldFailWhenMainTeacherIdsIsNull() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    null, List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "MAIN_TEACHER_REQUIRED");
            assertErrorPresent(results.get(0), err, "mainTeacherIds");
        }

        @Test
        void shouldFailWhenMainTeacherIdsIsEmpty() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of(), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "MAIN_TEACHER_REQUIRED");
            assertErrorPresent(results.get(0), err, "mainTeacherIds");
        }
    }

    // -------------------------------------------------------------------------
    // TEACHER_NOT_FOUND (main teacher)
    // -------------------------------------------------------------------------

    @Nested
    class MainTeacherNotFoundTest {

        @Test
        void shouldFailWhenMainTeacherIdNotInUserMap() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-unknown"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "TEACHER_NOT_FOUND");
            assertErrorPresent(results.get(0), err, "mainTeacherIds");
            assertTrue(err.message().contains("teacher-unknown"));
        }
    }

    // -------------------------------------------------------------------------
    // TEACHER_NOT_FOUND (assistant teacher)
    // -------------------------------------------------------------------------

    @Nested
    class AssistantTeacherNotFoundTest {

        @Test
        void shouldFailWhenAssistantTeacherIdNotInUserMap() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"),
                    List.of("teacher-unknown-asst"), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "TEACHER_NOT_FOUND");
            assertErrorPresent(results.get(0), err, "assistantTeacherIds");
            assertTrue(err.message().contains("teacher-unknown-asst"));
        }

        @Test
        void shouldNotReportTeacherNotFoundForAssistantWhenListIsNull() {
            // assistantTeacherIds = null is skipped entirely
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), null, null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            long teacherNotFoundCount = results.get(0).errors().stream()
                    .filter(e -> "TEACHER_NOT_FOUND".equals(e.code()))
                    .count();
            assertEquals(0, teacherNotFoundCount);
        }
    }

    // -------------------------------------------------------------------------
    // SUBJECT_NOT_FOUND
    // -------------------------------------------------------------------------

    @Nested
    class SubjectNotFoundTest {

        @Test
        void shouldFailWhenSubjectIdNotInSubjectMap() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-unknown", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "SUBJECT_NOT_FOUND");
            assertErrorPresent(results.get(0), err, "subjectId");
            assertTrue(err.message().contains("subj-unknown"));
        }
    }

    // -------------------------------------------------------------------------
    // LEVEL_NOT_FOUND
    // -------------------------------------------------------------------------

    @Nested
    class LevelNotFoundTest {

        @Test
        void shouldFailWhenLevelIdNotInLevelMap() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", "level-unknown", null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "LEVEL_NOT_FOUND");
            assertErrorPresent(results.get(0), err, "levelId");
            assertTrue(err.message().contains("level-unknown"));
        }
    }

    // -------------------------------------------------------------------------
    // LEVEL_NOT_IN_SUBJECT
    // -------------------------------------------------------------------------

    @Nested
    class LevelNotInSubjectTest {

        @Test
        void shouldFailWhenLevelBelongsToDifferentSubject() {
            // level-other belongs to subj-physics, but row subject is subj-math
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", "level-other", null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "LEVEL_NOT_IN_SUBJECT");
            assertErrorPresent(results.get(0), err, "levelId");
        }

        @Test
        void shouldNotReportLevelNotInSubjectWhenLevelNotFoundAlreadyReported() {
            // level-unknown is neither found -> only LEVEL_NOT_FOUND
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", "level-unknown", null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            assertTrue(findError(results.get(0), "LEVEL_NOT_FOUND") != null);
            assertTrue(findError(results.get(0), "LEVEL_NOT_IN_SUBJECT") == null);
        }
    }

    // -------------------------------------------------------------------------
    // DUPLICATE_TEACHER_ROLE
    // -------------------------------------------------------------------------

    @Nested
    class DuplicateTeacherRoleTest {

        @Test
        void shouldFailWhenSameTeacherIdInBothMainAndAssistant() {
            // teacher-main-1 appears in both lists
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"),
                    List.of("teacher-main-1"), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "DUPLICATE_TEACHER_ROLE");
            assertErrorPresent(results.get(0), err, "assistantTeacherIds");
        }

        @Test
        void shouldFailWhenMultipleAssistantIdsDuplicateMainIds() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"),
                    List.of("teacher-main-1", "teacher-assistant-1"), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            // Should have duplicate for teacher-main-1 only, not teacher-assistant-1
            long duplicateCount = results.get(0).errors().stream()
                    .filter(e -> "DUPLICATE_TEACHER_ROLE".equals(e.code()))
                    .count();
            assertEquals(1, duplicateCount, "Only one duplicate should be reported");
        }

        @Test
        void shouldNotReportDuplicateWhenAssistantTeacherIdsIsNull() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), null, null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            long duplicateCount = results.get(0).errors().stream()
                    .filter(e -> "DUPLICATE_TEACHER_ROLE".equals(e.code()))
                    .count();
            assertEquals(0, duplicateCount);
        }
    }

    // -------------------------------------------------------------------------
    // INVALID_PERIOD_COUNT
    // -------------------------------------------------------------------------

    @Nested
    class InvalidPeriodCountTest {

        @Test
        void shouldFailWhenPeriodCountIsZero() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 0,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_PERIOD_COUNT");
            assertErrorPresent(results.get(0), err, "periodCount");
        }

        @Test
        void shouldFailWhenPeriodCountIsGreaterThanTen() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 11,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_PERIOD_COUNT");
            assertErrorPresent(results.get(0), err, "periodCount");
        }

        @Test
        void shouldFailWhenPeriodCountIsNegative() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, -1,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_PERIOD_COUNT");
            assertErrorPresent(results.get(0), err, "periodCount");
        }
    }

    // -------------------------------------------------------------------------
    // INVALID_PERIOD_START
    // -------------------------------------------------------------------------

    @Nested
    class InvalidPeriodStartTest {

        @Test
        void shouldFailWhenPeriodStartIsZero() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 0, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_PERIOD_START");
            assertErrorPresent(results.get(0), err, "periodStart");
        }

        @Test
        void shouldFailWhenPeriodStartIsNegative() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", -5, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_PERIOD_START");
            assertErrorPresent(results.get(0), err, "periodStart");
        }
    }

    // -------------------------------------------------------------------------
    // INVALID_TIME_RANGE
    // -------------------------------------------------------------------------

    @Nested
    class InvalidTimeRangeTest {

        @Test
        void shouldFailWhenStartTimeEqualsEndTime() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "08:00", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_TIME_RANGE");
            assertErrorPresent(results.get(0), err, "startTime");
        }

        @Test
        void shouldFailWhenStartTimeIsAfterEndTime() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "10:30", "08:00", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_TIME_RANGE");
            assertErrorPresent(results.get(0), err, "startTime");
        }

        @Test
        void shouldNotReportTimeFormatErrorWhenTimeRangeIsInvalid() {
            // Both start and end parse OK, but start >= end -> only INVALID_TIME_RANGE
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "09:00", "09:00", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            assertEquals(1, results.get(0).errors().size());
            assertEquals("INVALID_TIME_RANGE", results.get(0).errors().get(0).code());
        }
    }

    // -------------------------------------------------------------------------
    // INVALID_TIME_FORMAT
    // -------------------------------------------------------------------------

    @Nested
    class InvalidTimeFormatTest {

        @Test
        void shouldFailWhenStartTimeIsMalformed() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "not-a-time", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_TIME_FORMAT");
            assertErrorPresent(results.get(0), err, "startTime");
        }

        @Test
        void shouldFailWhenEndTimeIsMalformed() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 2, "MORNING", 1, 3,
                    "08:00", "bad-time", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_TIME_FORMAT");
            assertErrorPresent(results.get(0), err, "startTime");
        }
    }

    // -------------------------------------------------------------------------
    // INVALID_WEEKDAY
    // -------------------------------------------------------------------------

    @Nested
    class InvalidWeekdayTest {

        @Test
        void shouldFailWhenWeekdayIsZero() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 0, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_WEEKDAY");
            assertErrorPresent(results.get(0), err, "weekday");
        }

        @Test
        void shouldFailWhenWeekdayIsEight() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), 8, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_WEEKDAY");
            assertErrorPresent(results.get(0), err, "weekday");
        }

        @Test
        void shouldFailWhenWeekdayIsNegative() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-1", List.of("class-a"), -1, "MORNING", 1, 3,
                    "08:00", "10:30", "subj-math", null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            CalendarRowMessageResponse err = findError(results.get(0), "INVALID_WEEKDAY");
            assertErrorPresent(results.get(0), err, "weekday");
        }
    }

    // -------------------------------------------------------------------------
    // Single row with many errors at once
    // -------------------------------------------------------------------------

    @Nested
    class MultipleErrorsSingleRowTest {

        @Test
        void shouldCollectAllErrorsInOneRow() {
            CalendarBulkRowRequest row = new CalendarBulkRowRequest(
                    "row-multi",
                    null,           // CLASS_REQUIRED
                    0,              // INVALID_WEEKDAY
                    "MORNING",
                    0,              // INVALID_PERIOD_START
                    0,              // INVALID_PERIOD_COUNT
                    "bad-time",     // INVALID_TIME_FORMAT
                    "also-bad",     // (same catch block, still INVALID_TIME_FORMAT)
                    "subj-unknown", // SUBJECT_NOT_FOUND
                    "level-unknown",// LEVEL_NOT_FOUND
                    null,
                    null,
                    List.of("teacher-unknown"),  // TEACHER_NOT_FOUND (main)
                    List.of(),      // no assistant
                    null
            );
            List<RowValidationResult> results = service.validateRows(
                    requestWithRows(row), refData);

            RowValidationResult result = results.get(0);
            assertFalse(result.valid());
            assertEquals("row-multi", result.clientRowId());

            Set<String> codes = result.errors().stream()
                    .map(CalendarRowMessageResponse::code)
                    .collect(Collectors.toSet());

            assertTrue(codes.contains("CLASS_REQUIRED"));
            assertTrue(codes.contains("INVALID_WEEKDAY"));
            assertTrue(codes.contains("INVALID_PERIOD_START"));
            assertTrue(codes.contains("INVALID_PERIOD_COUNT"));
            assertTrue(codes.contains("INVALID_TIME_FORMAT"));
            assertTrue(codes.contains("SUBJECT_NOT_FOUND"));
            assertTrue(codes.contains("LEVEL_NOT_FOUND"));
            assertTrue(codes.contains("TEACHER_NOT_FOUND"));
        }
    }

    // -------------------------------------------------------------------------
    // Multiple rows with mixed validity
    // -------------------------------------------------------------------------

    @Nested
    class MultipleRowsMixedValidityTest {

        @Test
        void shouldReturnResultsInOrder() {
            CalendarBulkRowRequest valid = validRow();
            CalendarBulkRowRequest invalid = new CalendarBulkRowRequest(
                    "row-bad",
                    null, 0, "MORNING", 0, 0,
                    "bad-time", "also-bad", "subj-unknown",
                    "level-unknown", null, null,
                    List.of("teacher-unknown"), List.of(), null
            );

            CalendarBulkCreateRequest request = requestWithRows(valid, invalid);

            List<RowValidationResult> results = service.validateRows(request, refData);

            assertEquals(2, results.size());
            assertEquals("row-1", results.get(0).clientRowId());
            assertTrue(results.get(0).valid());
            assertEquals("row-bad", results.get(1).clientRowId());
            assertFalse(results.get(1).valid());
        }

        @Test
        void shouldHandleSingleValidRowAmongMultipleRows() {
            CalendarBulkRowRequest valid1 = validRow();
            CalendarBulkRowRequest invalid1 = new CalendarBulkRowRequest(
                    "row-bad-1", null, 0, "MORNING", 0, 0,
                    "bad-time", "also-bad", "subj-unknown",
                    "level-unknown", null, null,
                    List.of("teacher-unknown"), List.of(), null
            );
            CalendarBulkRowRequest valid2 = new CalendarBulkRowRequest(
                    "row-good-2",
                    List.of("class-a"),
                    3,                  // Wednesday
                    "AFTERNOON",
                    3,
                    2,
                    "13:00",
                    "14:30",
                    "subj-math",
                    null,
                    "room-102",
                    "Room 102",
                    List.of("teacher-main-1"),
                    List.of(),
                    null
            );
            CalendarBulkRowRequest invalid2 = new CalendarBulkRowRequest(
                    "row-bad-2",
                    List.of("class-unknown"), 7, "MORNING", 0, 11,
                    "08:00", "08:00", "subj-unknown",
                    null, null, null,
                    List.of("teacher-main-1"), List.of(), null
            );

            CalendarBulkCreateRequest request = requestWithRows(valid1, invalid1, valid2, invalid2);

            List<RowValidationResult> results = service.validateRows(request, refData);

            assertEquals(4, results.size());
            assertTrue(results.get(0).valid(), "row-1 should be valid");
            assertFalse(results.get(1).valid(), "row-bad-1 should be invalid");
            assertTrue(results.get(2).valid(), "row-good-2 should be valid");
            assertFalse(results.get(3).valid(), "row-bad-2 should be invalid");
        }
    }

    // -------------------------------------------------------------------------
    // Assertion helper
    // -------------------------------------------------------------------------

    private static void assertErrorPresent(
            RowValidationResult result,
            CalendarRowMessageResponse err,
            String expectedField
    ) {
        assertFalse(result.valid(), "Result should be marked invalid when error '"
                + (err != null ? err.code() : "?") + "' is present");
        assertTrue(err != null, "Expected error not found in result: " + result);
        assertEquals(expectedField, err.field());
        assertTrue(err.message() != null && !err.message().isBlank(),
                "Error message should not be blank");
    }
}
