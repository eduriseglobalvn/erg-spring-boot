package com.erg.ergspringboot.controller.calendar.graphql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.TeachingScheduleBatchDetailRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.TeachingScheduleBatchListRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarWorkspaceRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchDetailResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPageResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchSummaryResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import com.erg.ergspringboot.service.calendar.CalendarReadService;
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
class CalendarLcmsQueryGraphQlControllerTest {

    @Mock
    private CalendarReadService readService;

    @Mock
    private CalendarEventRepository eventRepository;

    @Mock
    private CalendarBatchRepository batchRepository;

    private CalendarLcmsQueryGraphQlController controller;

    @BeforeEach
    void setUp() {
        GraphQlAccessSupport accessSupport = new GraphQlAccessSupport(
                new com.erg.ergspringboot.platform.security.ErgPolicyService());
        CalendarScopeService scopeService = new CalendarScopeService(accessSupport, eventRepository, batchRepository);
        controller = new CalendarLcmsQueryGraphQlController(readService, accessSupport, scopeService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // teachingCalendarWorkspace
    // =========================================================================

    @Nested
    class TeachingCalendarWorkspace {

        private final CalendarWorkspaceRequest input = new CalendarWorkspaceRequest(
                null, "2026-01-01", "2026-01-31", "SCHOOL",
                List.of("school-1"), List.of(), List.of(), List.of(), List.of(), List.of());

        @Test
        void succeedsWithReadPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "tenant-a", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of(), null, List.of(), null, null, "now");
            when(readService.lcmsTeachingCalendarWorkspace(
                    "tenant-a", List.of("school-1"), "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of(), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", List.of("lms.schedule.read", "lms.manage"),
                            List.of(), List.of("school-1"))))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.teachingCalendarWorkspace(input);

            verify(readService).lcmsTeachingCalendarWorkspace(
                    "tenant-a", List.of("school-1"), "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of(), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", List.of("lms.schedule.read", "lms.manage"),
                            List.of(), List.of("school-1")));
            assert result.equals(expected);
        }

        @Test
        void succeedsWithManagePermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "tenant-a", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of(), null, List.of(), null, null, "now");
            when(readService.lcmsTeachingCalendarWorkspace(
                    "tenant-a", List.of("school-1"), "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of(), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", List.of("lms.schedule.manage"),
                            List.of(), List.of("school-1"))))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.teachingCalendarWorkspace(input);

            verify(readService).lcmsTeachingCalendarWorkspace(
                    "tenant-a", List.of("school-1"), "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of(), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", List.of("lms.schedule.manage"),
                            List.of(), List.of("school-1")));
            assert result.equals(expected);
        }

        @Test
        void rejectsMissingPermission() {
            authenticate(principal("tenant-a",
                    List.of("some.other.permission"),
                    List.of(),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.teachingCalendarWorkspace(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsDeniedPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read", "lms.schedule.manage", "lms.manage", "*"),
                    List.of("*"),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.teachingCalendarWorkspace(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsDeniedSpecificPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read", "lms.schedule.manage"),
                    List.of("lms.schedule.read"),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.teachingCalendarWorkspace(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsUnauthenticatedAccess() {
            assertThatThrownBy(() -> controller.teachingCalendarWorkspace(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsTenantSpoofing() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read", "lms.manage"),
                    List.of(),
                    List.of("school-1")));
            CalendarWorkspaceRequest otherTenantInput = new CalendarWorkspaceRequest(
                    "tenant-b", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of("school-1"), List.of(), List.of(), List.of(), List.of(), List.of());

            assertThatThrownBy(() -> controller.teachingCalendarWorkspace(otherTenantInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void usesPrincipalTenantIdWhenRequestTenantIdIsNull() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "tenant-a", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of(), null, List.of(), null, null, "now");
            when(readService.lcmsTeachingCalendarWorkspace(
                    "tenant-a", List.of("school-1"), "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of(), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", List.of("lms.schedule.read"),
                            List.of(), List.of("school-1"))))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.teachingCalendarWorkspace(input);

            verify(readService).lcmsTeachingCalendarWorkspace(
                    "tenant-a", List.of("school-1"), "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of(), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", List.of("lms.schedule.read"),
                            List.of(), List.of("school-1")));
            assert result.equals(expected);
        }

        @Test
        void wildcardPrincipalSucceeds() {
            authenticate(ErgPrincipal.systemAdmin());
            CalendarWorkspaceRequest noTenantInput = new CalendarWorkspaceRequest(
                    null, "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of("school-1"), List.of(), List.of(), List.of(), List.of(), List.of());
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "erg", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of(), null, List.of(), null, null, "now");
            when(readService.lcmsTeachingCalendarWorkspace(
                    "erg", List.of("school-1"), "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of(), List.of(), List.of(), List.of(), List.of(),
                    ErgPrincipal.systemAdmin()))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.teachingCalendarWorkspace(noTenantInput);

            verify(readService).lcmsTeachingCalendarWorkspace(
                    "erg", List.of("school-1"), "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of(), List.of(), List.of(), List.of(), List.of(),
                    ErgPrincipal.systemAdmin());
            assert result.equals(expected);
        }

        @Test
        void resolvesSchoolScopeFromPrincipalAttributesWhenInputSchoolIdsEmpty() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of(),          // managedClassIds empty
                    List.of("school-1", "school-2")));
            CalendarWorkspaceRequest noSchoolInput = new CalendarWorkspaceRequest(
                    null, "2026-01-01", "2026-01-31", "SCHOOL",
                    Collections.emptyList(), List.of(), List.of(), List.of(), List.of(), List.of());
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "tenant-a", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of(), null, List.of(), null, null, "now");
            when(readService.lcmsTeachingCalendarWorkspace(
                    "tenant-a", List.of("school-1", "school-2"), "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of(), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", List.of("lms.schedule.read"),
                            List.of(), List.of(), List.of("school-1", "school-2"))))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.teachingCalendarWorkspace(noSchoolInput);

            verify(readService).lcmsTeachingCalendarWorkspace(
                    "tenant-a", List.of("school-1", "school-2"), "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of(), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", List.of("lms.schedule.read"),
                            List.of(), List.of(), List.of("school-1", "school-2")));
            assert result.equals(expected);
        }

        @Test
        void rejectsInputSchoolIdsOutsideManagedScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of(),
                    List.of("school-3", "school-4")));

            assertThatThrownBy(() -> controller.teachingCalendarWorkspace(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsEmptySchoolScopeWhenNoManagedIdsAvailable() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of(),
                    List.of()));
            CalendarWorkspaceRequest noSchoolInput = new CalendarWorkspaceRequest(
                    null, "2026-01-01", "2026-01-31", "SCHOOL",
                    Collections.emptyList(), List.of(), List.of(), List.of(), List.of(), List.of());

            assertThatThrownBy(() -> controller.teachingCalendarWorkspace(noSchoolInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void managePermissionDoesNotBypassManagedSchoolScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.manage"),
                    List.of(),
                    List.of(),
                    List.of()));
            CalendarWorkspaceRequest noSchoolInput = new CalendarWorkspaceRequest(
                    null, "2026-01-01", "2026-01-31", "SCHOOL",
                    Collections.emptyList(), List.of(), List.of(), List.of(), List.of(), List.of());

            assertThatThrownBy(() -> controller.teachingCalendarWorkspace(noSchoolInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void handlesNullInputGracefully() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "tenant-a", null, null, null,
                    List.of(), null, List.of(), null, null, "now");
            when(readService.lcmsTeachingCalendarWorkspace(
                    "tenant-a", List.of("school-1"), null, null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", List.of("lms.schedule.read"),
                            List.of(), List.of("school-1"))))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.teachingCalendarWorkspace(null);

            verify(readService).lcmsTeachingCalendarWorkspace(
                    "tenant-a", List.of("school-1"), null, null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", List.of("lms.schedule.read"),
                            List.of(), List.of("school-1")));
            assert result.equals(expected);
        }

        @Test
        void rejectsNullInputWhenPrincipalHasNoManagedSchools() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of(),
                    List.of()));

            assertThatThrownBy(() -> controller.teachingCalendarWorkspace(null))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsNullInputWithoutAuthentication() {
            assertThatThrownBy(() -> controller.teachingCalendarWorkspace(null))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }
    }

    // =========================================================================
    // teachingScheduleBatches / teachingScheduleBatch
    // =========================================================================

    @Nested
    class TeachingScheduleBatches {

        @Test
        void listsBatchesWithReadPermissionAndSchoolScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));
            TeachingScheduleBatchListRequest input = new TeachingScheduleBatchListRequest(
                    null, "school-1", "DRAFT", 0, 20);
            CalendarBatchSummaryResponse summary = batchSummary("batch-1", "DRAFT");
            CalendarBatchPageResponse expected = new CalendarBatchPageResponse(
                    List.of(summary), 0, 20, 1, 1, false, false);
            when(readService.teachingScheduleBatches("tenant-a", "school-1", "DRAFT", 0, 20))
                    .thenReturn(expected);

            CalendarBatchPageResponse result = controller.teachingScheduleBatches(input);

            verify(readService).teachingScheduleBatches("tenant-a", "school-1", "DRAFT", 0, 20);
            assert result.equals(expected);
        }

        @Test
        void rejectsBatchListWhenSchoolIsOutOfScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));
            TeachingScheduleBatchListRequest input = new TeachingScheduleBatchListRequest(
                    null, "school-2", null, null, null);

            assertThatThrownBy(() -> controller.teachingScheduleBatches(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsBatchListWithoutPermission() {
            authenticate(principal("tenant-a",
                    List.of("some.other.permission"),
                    List.of(),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.teachingScheduleBatches(
                    new TeachingScheduleBatchListRequest(null, "school-1", null, null, null)))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void returnsBatchDetailWithReadPermission() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));
            TeachingScheduleBatchDetailRequest input = new TeachingScheduleBatchDetailRequest(null, "batch-1");
            CalendarBatch batch = new CalendarBatch();
            batch.setId("batch-1");
            batch.setTenantId("tenant-a");
            batch.setSchoolId("school-1");
            when(batchRepository.findByTenantIdAndId("tenant-a", "batch-1")).thenReturn(Optional.of(batch));
            CalendarBatchDetailResponse expected = batchDetail("batch-1", "PUBLISHED", 12);
            when(readService.teachingScheduleBatch("tenant-a", "batch-1")).thenReturn(expected);

            CalendarBatchDetailResponse result = controller.teachingScheduleBatch(input);

            verify(readService).teachingScheduleBatch("tenant-a", "batch-1");
            assert result.equals(expected);
        }

        @Test
        void rejectsBatchDetailOutsideManagedSchoolScope() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));
            CalendarBatch batch = new CalendarBatch();
            batch.setId("batch-2");
            batch.setTenantId("tenant-a");
            batch.setSchoolId("school-2");
            when(batchRepository.findByTenantIdAndId("tenant-a", "batch-2")).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> controller.teachingScheduleBatch(
                    new TeachingScheduleBatchDetailRequest(null, "batch-2")))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsBatchDetailWhenBatchIdMissing() {
            authenticate(principal("tenant-a",
                    List.of("lms.schedule.read"),
                    List.of(),
                    List.of("school-1")));

            assertThatThrownBy(() -> controller.teachingScheduleBatch(
                    new TeachingScheduleBatchDetailRequest(null, " ")))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(readService);
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

    private CalendarBatchSummaryResponse batchSummary(String id, String status) {
        return new CalendarBatchSummaryResponse(
                id, "tenant-a", "school-1", "2025-2026",
                "2026-06-15", 4, status, "MANUAL", "note",
                "creator-1", null, null, "2026-06-15T07:00:00+07:00");
    }

    private CalendarBatchDetailResponse batchDetail(String id, String status, int eventCount) {
        return new CalendarBatchDetailResponse(
                id, "tenant-a", "school-1", "2025-2026",
                "2026-06-15", 4, status, "MANUAL", "note",
                "creator-1", "publisher-1", "2026-06-15T08:00:00+07:00",
                "2026-06-15T07:00:00+07:00", eventCount);
    }

}
