package com.erg.ergspringboot.controller.calendar.graphql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CreateRoomRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.DeleteTeacherScheduleRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBulkPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarDeletePayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPreviewPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOption;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.service.calendar.CalendarCommandService;
import com.erg.ergspringboot.service.calendar.CalendarScopeService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CalendarMutationGraphQlControllerTest {

    @Mock
    private CalendarCommandService commandService;

    @Mock
    private CalendarEventRepository eventRepository;

    @Mock
    private CalendarBatchRepository batchRepository;

    private CalendarMutationGraphQlController controller;

    @BeforeEach
    void setUp() {
        GraphQlAccessSupport accessSupport = new GraphQlAccessSupport(
                new com.erg.ergspringboot.platform.security.ErgPolicyService());
        CalendarScopeService scopeService = new CalendarScopeService(accessSupport, eventRepository, batchRepository);
        controller = new CalendarMutationGraphQlController(commandService, accessSupport, scopeService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // previewTeachingSchedule
    // =========================================================================

    @Nested
    class PreviewTeachingSchedule {

        private final CalendarBulkCreateRequest input = new CalendarBulkCreateRequest(
                null, "idem-1", "school-1", "2026", "2026-01-01",
                1, null, false, null, List.of());

        @Test
        void succeedsWithRequiredPermissionAndScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarPreviewPayload expected = new CalendarPreviewPayload(
                    true, 5, 3, 20, List.of(), List.of());
            when(commandService.preview("tenant-a", input, "teacher-1")).thenReturn(expected);

            CalendarPreviewPayload result = controller.previewTeachingSchedule(input);

            verify(commandService).preview("tenant-a", input, "teacher-1");
            assert result.equals(expected);
        }

        @Test
        void rejectsMissingPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.previewTeachingSchedule(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsDeniedPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of("lms.schedule.manage"),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.previewTeachingSchedule(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsTenantSpoofing() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarBulkCreateRequest otherTenantInput = new CalendarBulkCreateRequest(
                    "tenant-b", "idem-1", "school-1", "2026", "2026-01-01",
                    1, null, false, null, List.of());

            assertThatThrownBy(() -> controller.previewTeachingSchedule(otherTenantInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsUnmanagedSchoolScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarBulkCreateRequest otherSchoolInput = new CalendarBulkCreateRequest(
                    null, "idem-1", "school-2", "2026", "2026-01-01",
                    1, null, false, null, List.of());

            assertThatThrownBy(() -> controller.previewTeachingSchedule(otherSchoolInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsUnauthenticatedAccess() {
            assertThatThrownBy(() -> controller.previewTeachingSchedule(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void wildcardPrincipalBypassesScopeCheck() {
            authenticate(ErgPrincipal.systemAdmin());
            CalendarPreviewPayload expected = new CalendarPreviewPayload(
                    true, 5, 3, 20, List.of(), List.of());
            when(commandService.preview("erg", input, "system")).thenReturn(expected);

            CalendarPreviewPayload result = controller.previewTeachingSchedule(input);

            verify(commandService).preview("erg", input, "system");
            assert result.equals(expected);
        }
    }

    // =========================================================================
    // createTeachingSchedule
    // =========================================================================

    @Nested
    class CreateTeachingSchedule {

        private final CalendarBulkCreateRequest input = new CalendarBulkCreateRequest(
                null, "idem-2", "school-1", "2026", "2026-01-01",
                2, null, false, null, List.of());

        @Test
        void succeedsWithRequiredPermissionAndScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarBulkPayload expected = new CalendarBulkPayload(
                    "batch-1", 10, 6, 40, List.of(), List.of());
            when(commandService.create("tenant-a", input, "teacher-1")).thenReturn(expected);

            CalendarBulkPayload result = controller.createTeachingSchedule(input);

            verify(commandService).create("tenant-a", input, "teacher-1");
            assert result.equals(expected);
        }

        @Test
        void rejectsMissingPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.createTeachingSchedule(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsDeniedPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of("lms.manage"),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.createTeachingSchedule(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsTenantSpoofing() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarBulkCreateRequest otherTenantInput = new CalendarBulkCreateRequest(
                    "tenant-b", "idem-2", "school-1", "2026", "2026-01-01",
                    2, null, false, null, List.of());

            assertThatThrownBy(() -> controller.createTeachingSchedule(otherTenantInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsUnmanagedSchoolScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarBulkCreateRequest otherSchoolInput = new CalendarBulkCreateRequest(
                    null, "idem-2", "school-2", "2026", "2026-01-01",
                    2, null, false, null, List.of());

            assertThatThrownBy(() -> controller.createTeachingSchedule(otherSchoolInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void wildcardPrincipalBypassesScopeCheck() {
            authenticate(ErgPrincipal.systemAdmin());
            CalendarBulkPayload expected = new CalendarBulkPayload(
                    "batch-1", 10, 6, 40, List.of(), List.of());
            when(commandService.create("erg", input, "system")).thenReturn(expected);

            CalendarBulkPayload result = controller.createTeachingSchedule(input);

            verify(commandService).create("erg", input, "system");
            assert result.equals(expected);
        }
    }

    // =========================================================================
    // updateTeachingScheduleEvent
    // =========================================================================

    @Nested
    class UpdateTeachingScheduleEvent {

        private final CalendarEventUpdateRequest input = new CalendarEventUpdateRequest(
                null, "event-1", "room-1", "Room A", "Updated note");

        @Test
        void succeedsWithRequiredPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            when(eventRepository.findByTenantIdAndId("tenant-a", "event-1"))
                    .thenReturn(Optional.of(calendarEvent("event-1", "school-1")));
            CalendarEventPayload expected = new CalendarEventPayload(null);
            when(commandService.updateEvent("tenant-a", input, "teacher-1")).thenReturn(expected);

            CalendarEventPayload result = controller.updateTeachingScheduleEvent(input);

            verify(commandService).updateEvent("tenant-a", input, "teacher-1");
            assert result.equals(expected);
        }

        @Test
        void rejectsMissingPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.updateTeachingScheduleEvent(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsDeniedPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of("lms.schedule.manage"),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.updateTeachingScheduleEvent(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsTenantSpoofing() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarEventUpdateRequest otherTenantInput = new CalendarEventUpdateRequest(
                    "tenant-b", "event-1", "room-1", "Room A", "Updated note");

            assertThatThrownBy(() -> controller.updateTeachingScheduleEvent(otherTenantInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsMissingAuthentication() {
            assertThatThrownBy(() -> controller.updateTeachingScheduleEvent(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void wildcardPrincipalSucceeds() {
            authenticate(ErgPrincipal.systemAdmin());
            when(eventRepository.findByTenantIdAndId("erg", "event-1"))
                    .thenReturn(Optional.of(calendarEvent("event-1", "school-1")));
            CalendarEventPayload expected = new CalendarEventPayload(null);
            when(commandService.updateEvent("erg", input, "system")).thenReturn(expected);

            CalendarEventPayload result = controller.updateTeachingScheduleEvent(input);

            verify(commandService).updateEvent("erg", input, "system");
            assert result.equals(expected);
        }

        @Test
        void rejectsEventOutsideManagedSchoolScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage"), List.of(), List.of("school-1")));
            when(eventRepository.findByTenantIdAndId("tenant-a", "event-1"))
                    .thenReturn(Optional.of(calendarEvent("event-1", "school-2")));

            assertThatThrownBy(() -> controller.updateTeachingScheduleEvent(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }
    }

    // =========================================================================
    // deleteTeachingScheduleEvent
    // =========================================================================

    @Nested
    class DeleteTeachingScheduleEvent {

        private final CalendarEventDeleteRequest input = new CalendarEventDeleteRequest(null, "event-1");

        @Test
        void succeedsWithRequiredPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            when(eventRepository.findByTenantIdAndId("tenant-a", "event-1"))
                    .thenReturn(Optional.of(calendarEvent("event-1", "school-1")));
            CalendarDeletePayload expected = new CalendarDeletePayload("event-1", true, 1);
            when(commandService.deleteEvent("tenant-a", input, "teacher-1")).thenReturn(expected);

            CalendarDeletePayload result = controller.deleteTeachingScheduleEvent(input);

            verify(commandService).deleteEvent("tenant-a", input, "teacher-1");
            assert result.equals(expected);
        }

        @Test
        void rejectsMissingPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.deleteTeachingScheduleEvent(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsDeniedPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of("lms.schedule.manage"),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.deleteTeachingScheduleEvent(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsTenantSpoofing() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarEventDeleteRequest otherTenantInput = new CalendarEventDeleteRequest("tenant-b", "event-1");

            assertThatThrownBy(() -> controller.deleteTeachingScheduleEvent(otherTenantInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsMissingAuthentication() {
            assertThatThrownBy(() -> controller.deleteTeachingScheduleEvent(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void wildcardPrincipalSucceeds() {
            authenticate(ErgPrincipal.systemAdmin());
            when(eventRepository.findByTenantIdAndId("erg", "event-1"))
                    .thenReturn(Optional.of(calendarEvent("event-1", "school-1")));
            CalendarDeletePayload expected = new CalendarDeletePayload("event-1", true, 1);
            when(commandService.deleteEvent("erg", input, "system")).thenReturn(expected);

            CalendarDeletePayload result = controller.deleteTeachingScheduleEvent(input);

            verify(commandService).deleteEvent("erg", input, "system");
            assert result.equals(expected);
        }

        @Test
        void rejectsEventOutsideManagedSchoolScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage"), List.of(), List.of("school-1")));
            when(eventRepository.findByTenantIdAndId("tenant-a", "event-1"))
                    .thenReturn(Optional.of(calendarEvent("event-1", "school-2")));

            assertThatThrownBy(() -> controller.deleteTeachingScheduleEvent(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }
    }

    // =========================================================================
    // deleteTeacherSchedule
    // =========================================================================

    @Nested
    class DeleteTeacherSchedule {

        private final DeleteTeacherScheduleRequest input = new DeleteTeacherScheduleRequest(
                null, "teacher-2", List.of("school-1"), "2026-01-01", "2026-01-31");

        @Test
        void succeedsWithRequiredPermissionAndScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarDeletePayload expected = new CalendarDeletePayload("teacher-2", true, 8);
            when(commandService.deleteTeacherSchedule(
                    "tenant-a", "teacher-2", List.of("school-1"),
                    "2026-01-01", "2026-01-31", "teacher-1"))
                    .thenReturn(expected);

            CalendarDeletePayload result = controller.deleteTeacherSchedule(input);

            verify(commandService).deleteTeacherSchedule(
                    "tenant-a", "teacher-2", List.of("school-1"),
                    "2026-01-01", "2026-01-31", "teacher-1");
            assert result.equals(expected);
        }

        @Test
        void rejectsMissingPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.deleteTeacherSchedule(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsDeniedPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of("lms.manage"),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.deleteTeacherSchedule(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsTenantSpoofing() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            DeleteTeacherScheduleRequest otherTenantInput = new DeleteTeacherScheduleRequest(
                    "tenant-b", "teacher-2", List.of("school-1"), "2026-01-01", "2026-01-31");

            assertThatThrownBy(() -> controller.deleteTeacherSchedule(otherTenantInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void resolvesSchoolScopeFromPrincipalWhenInputSchoolIdsAreEmpty() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of(),      // managedClassIds empty
                    List.of("school-1", "school-2")));
            DeleteTeacherScheduleRequest noSchoolInput = new DeleteTeacherScheduleRequest(
                    null, "teacher-2", Collections.emptyList(), "2026-01-01", "2026-01-31");
            CalendarDeletePayload expected = new CalendarDeletePayload("teacher-2", true, 5);
            when(commandService.deleteTeacherSchedule(
                    "tenant-a", "teacher-2", List.of("school-1", "school-2"),
                    "2026-01-01", "2026-01-31", "teacher-1"))
                    .thenReturn(expected);

            CalendarDeletePayload result = controller.deleteTeacherSchedule(noSchoolInput);

            verify(commandService).deleteTeacherSchedule(
                    "tenant-a", "teacher-2", List.of("school-1", "school-2"),
                    "2026-01-01", "2026-01-31", "teacher-1");
            assert result.equals(expected);
        }

        @Test
        void rejectsEmptySchoolScopeWhenNoManagedIdsAvailable() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of(),      // managedClassIds empty
                    List.of()));    // no managedEducationUnitIds
            DeleteTeacherScheduleRequest noSchoolInput = new DeleteTeacherScheduleRequest(
                    null, "teacher-2", Collections.emptyList(), "2026-01-01", "2026-01-31");
            assertThatThrownBy(() -> controller.deleteTeacherSchedule(noSchoolInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsRequestedSchoolOutsideManagedScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage"), List.of(), List.of("school-1")));
            DeleteTeacherScheduleRequest outsideScope = new DeleteTeacherScheduleRequest(
                    null, "teacher-2", List.of("school-2"), "2026-01-01", "2026-01-31");

            assertThatThrownBy(() -> controller.deleteTeacherSchedule(outsideScope))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void wildcardPrincipalSucceeds() {
            authenticate(ErgPrincipal.systemAdmin());
            CalendarDeletePayload expected = new CalendarDeletePayload("teacher-2", true, 8);
            when(commandService.deleteTeacherSchedule(
                    "erg", "teacher-2", List.of("school-1"),
                    "2026-01-01", "2026-01-31", "system"))
                    .thenReturn(expected);

            CalendarDeletePayload result = controller.deleteTeacherSchedule(input);

            verify(commandService).deleteTeacherSchedule(
                    "erg", "teacher-2", List.of("school-1"),
                    "2026-01-01", "2026-01-31", "system");
            assert result.equals(expected);
        }

    }

    // =========================================================================
    // publishTeachingScheduleBatch
    // =========================================================================

    @Nested
    class PublishTeachingScheduleBatch {

        private static final String BATCH_ID = "batch-p1";

        @Test
        void succeedsWithRequiredPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            when(batchRepository.findByTenantIdAndId("tenant-a", BATCH_ID))
                    .thenReturn(Optional.of(calendarBatch(BATCH_ID, "school-1")));
            CalendarBatchPayload expected = new CalendarBatchPayload(BATCH_ID, "PUBLISHED", 10);
            when(commandService.publishBatch("tenant-a", BATCH_ID, "teacher-1")).thenReturn(expected);

            CalendarBatchPayload result = controller.publishTeachingScheduleBatch(BATCH_ID);

            verify(commandService).publishBatch("tenant-a", BATCH_ID, "teacher-1");
            assert result.equals(expected);
        }

        @Test
        void rejectsMissingPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of()));

            assertThatThrownBy(() -> controller.publishTeachingScheduleBatch(BATCH_ID))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsDeniedPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of("lms.schedule.manage"),
                    List.of()));

            assertThatThrownBy(() -> controller.publishTeachingScheduleBatch(BATCH_ID))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsMissingAuthentication() {
            assertThatThrownBy(() -> controller.publishTeachingScheduleBatch(BATCH_ID))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void wildcardPrincipalSucceeds() {
            authenticate(ErgPrincipal.systemAdmin());
            when(batchRepository.findByTenantIdAndId("erg", BATCH_ID))
                    .thenReturn(Optional.of(calendarBatch(BATCH_ID, "school-1")));
            CalendarBatchPayload expected = new CalendarBatchPayload(BATCH_ID, "PUBLISHED", 10);
            when(commandService.publishBatch("erg", BATCH_ID, "system")).thenReturn(expected);

            CalendarBatchPayload result = controller.publishTeachingScheduleBatch(BATCH_ID);

            verify(commandService).publishBatch("erg", BATCH_ID, "system");
            assert result.equals(expected);
        }

        @Test
        void rejectsBatchOutsideManagedSchoolScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage"), List.of(), List.of("school-1")));
            when(batchRepository.findByTenantIdAndId("tenant-a", BATCH_ID))
                    .thenReturn(Optional.of(calendarBatch(BATCH_ID, "school-2")));

            assertThatThrownBy(() -> controller.publishTeachingScheduleBatch(BATCH_ID))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }
    }

    // =========================================================================
    // cancelTeachingScheduleBatch
    // =========================================================================

    @Nested
    class CancelTeachingScheduleBatch {

        private static final String BATCH_ID = "batch-c1";

        @Test
        void succeedsWithRequiredPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            when(batchRepository.findByTenantIdAndId("tenant-a", BATCH_ID))
                    .thenReturn(Optional.of(calendarBatch(BATCH_ID, "school-1")));
            CalendarBatchPayload expected = new CalendarBatchPayload(BATCH_ID, "CANCELLED", 10);
            when(commandService.cancelBatch("tenant-a", BATCH_ID, "teacher-1")).thenReturn(expected);

            CalendarBatchPayload result = controller.cancelTeachingScheduleBatch(BATCH_ID);

            verify(commandService).cancelBatch("tenant-a", BATCH_ID, "teacher-1");
            assert result.equals(expected);
        }

        @Test
        void rejectsMissingPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of()));

            assertThatThrownBy(() -> controller.cancelTeachingScheduleBatch(BATCH_ID))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsDeniedPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of("lms.schedule.manage"),
                    List.of()));

            assertThatThrownBy(() -> controller.cancelTeachingScheduleBatch(BATCH_ID))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsMissingAuthentication() {
            assertThatThrownBy(() -> controller.cancelTeachingScheduleBatch(BATCH_ID))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void wildcardPrincipalSucceeds() {
            authenticate(ErgPrincipal.systemAdmin());
            when(batchRepository.findByTenantIdAndId("erg", BATCH_ID))
                    .thenReturn(Optional.of(calendarBatch(BATCH_ID, "school-1")));
            CalendarBatchPayload expected = new CalendarBatchPayload(BATCH_ID, "CANCELLED", 10);
            when(commandService.cancelBatch("erg", BATCH_ID, "system")).thenReturn(expected);

            CalendarBatchPayload result = controller.cancelTeachingScheduleBatch(BATCH_ID);

            verify(commandService).cancelBatch("erg", BATCH_ID, "system");
            assert result.equals(expected);
        }

        @Test
        void rejectsBatchOutsideManagedSchoolScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage"), List.of(), List.of("school-1")));
            when(batchRepository.findByTenantIdAndId("tenant-a", BATCH_ID))
                    .thenReturn(Optional.of(calendarBatch(BATCH_ID, "school-2")));

            assertThatThrownBy(() -> controller.cancelTeachingScheduleBatch(BATCH_ID))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }
    }

    // =========================================================================
    // createRoom
    // =========================================================================

    @Nested
    class CreateRoom {

        private final CreateRoomRequest input = new CreateRoomRequest(null, "school-1", "Physics Lab");

        @Test
        void succeedsWithRequiredPermissionAndScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarRoomOption expected = new CalendarRoomOption("room-new", "Physics Lab", "school-1");
            when(commandService.createRoom("tenant-a", "school-1", "Physics Lab", "teacher-1"))
                    .thenReturn(expected);

            CalendarRoomOption result = controller.createRoom(input);

            verify(commandService).createRoom("tenant-a", "school-1", "Physics Lab", "teacher-1");
            assert result.equals(expected);
        }

        @Test
        void rejectsMissingPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.createRoom(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsDeniedPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of("lms.schedule.manage"),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.createRoom(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsTenantSpoofing() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CreateRoomRequest otherTenantInput = new CreateRoomRequest("tenant-b", "school-1", "Physics Lab");

            assertThatThrownBy(() -> controller.createRoom(otherTenantInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsUnmanagedSchoolScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CreateRoomRequest otherSchoolInput = new CreateRoomRequest(null, "school-2", "Physics Lab");

            assertThatThrownBy(() -> controller.createRoom(otherSchoolInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void rejectsMissingAuthentication() {
            assertThatThrownBy(() -> controller.createRoom(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(commandService);
        }

        @Test
        void wildcardPrincipalBypassesScopeCheck() {
            authenticate(ErgPrincipal.systemAdmin());
            CalendarRoomOption expected = new CalendarRoomOption("room-new", "Physics Lab", "school-1");
            when(commandService.createRoom("erg", "school-1", "Physics Lab", "system"))
                    .thenReturn(expected);

            CalendarRoomOption result = controller.createRoom(input);

            verify(commandService).createRoom("erg", "school-1", "Physics Lab", "system");
            assert result.equals(expected);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void authenticate(ErgPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private ErgPrincipal principal(
            String tenantId,
            List<String> permissions,
            List<String> deniedPermissions,
            List<String> managedSchoolIds
    ) {
        return principal(tenantId, permissions, deniedPermissions, List.of(), managedSchoolIds);
    }

    private ErgPrincipal principal(
            String tenantId,
            List<String> permissions,
            List<String> deniedPermissions,
            List<String> managedClassIds,
            List<String> managedEducationUnitIds
    ) {
        return new ErgPrincipal(
                "teacher-1",
                "session-1",
                tenantId,
                List.of("teacher"),
                permissions,
                deniedPermissions,
                List.of("lms"),
                "teacher",
                "school",
                Map.of(
                        "managedClassIds", managedClassIds,
                        "managedEducationUnitIds", managedEducationUnitIds
                )
        );
    }

    private CalendarEvent calendarEvent(String id, String schoolId) {
        CalendarEvent event = new CalendarEvent();
        event.setId(id);
        event.setTenantId("tenant-a");
        event.setSchoolId(schoolId);
        return event;
    }

    private CalendarBatch calendarBatch(String id, String schoolId) {
        CalendarBatch batch = new CalendarBatch();
        batch.setId(id);
        batch.setTenantId("tenant-a");
        batch.setSchoolId(schoolId);
        return batch;
    }
}
