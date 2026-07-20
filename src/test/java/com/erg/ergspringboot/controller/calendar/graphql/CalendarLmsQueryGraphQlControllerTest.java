package com.erg.ergspringboot.controller.calendar.graphql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.MyCalendarRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.calendar.CalendarReadService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
class CalendarLmsQueryGraphQlControllerTest {

    @Mock
    private CalendarReadService readService;

    private CalendarLmsQueryGraphQlController controller;

    @BeforeEach
    void setUp() {
        controller = new CalendarLmsQueryGraphQlController(
                readService,
                new GraphQlAccessSupport(new com.erg.ergspringboot.platform.security.ErgPolicyService()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // myTeachingCalendar
    // =========================================================================

    @Nested
    class MyTeachingCalendar {

        private final MyCalendarRequest input = new MyCalendarRequest(
                null, "2026-01-01", "2026-01-31", "SCHOOL",
                List.of("school-1"), List.of(), List.of(), List.of(), List.of());

        @Test
        void succeedsWithReadSelfPermission() {
            authenticate(principal("tenant-a", "teacher-1",
                    List.of("lms.schedule.read_self", "lms.manage"),
                    List.of()));
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "tenant-a", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of(), null, List.of(), null, null, "now");
            when(readService.teacherTeachingCalendarWorkspace(
                    "tenant-a", "teacher-1", "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of("school-1"), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", "teacher-1",
                            List.of("lms.schedule.read_self", "lms.manage"),
                            List.of())))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.myTeachingCalendar(input);

            verify(readService).teacherTeachingCalendarWorkspace(
                    "tenant-a", "teacher-1", "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of("school-1"), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", "teacher-1",
                            List.of("lms.schedule.read_self", "lms.manage"),
                            List.of()));
            assert result.equals(expected);
        }

        @Test
        void succeedsWithManagePermission() {
            authenticate(principal("tenant-a", "teacher-1",
                    List.of("lms.schedule.manage"),
                    List.of()));
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "tenant-a", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of(), null, List.of(), null, null, "now");
            when(readService.teacherTeachingCalendarWorkspace(
                    "tenant-a", "teacher-1", "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of("school-1"), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", "teacher-1",
                            List.of("lms.schedule.manage"),
                            List.of())))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.myTeachingCalendar(input);

            verify(readService).teacherTeachingCalendarWorkspace(
                    "tenant-a", "teacher-1", "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of("school-1"), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", "teacher-1",
                            List.of("lms.schedule.manage"),
                            List.of()));
            assert result.equals(expected);
        }

        @Test
        void usesPrincipalUserIdAsTeacherUserId() {
            authenticate(principal("tenant-a", "custom-user-id",
                    List.of("lms.schedule.read_self"),
                    List.of()));
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "tenant-a", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of(), null, List.of(), null, null, "now");
            when(readService.teacherTeachingCalendarWorkspace(
                    "tenant-a", "custom-user-id", "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of("school-1"), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", "custom-user-id",
                            List.of("lms.schedule.read_self"),
                            List.of())))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.myTeachingCalendar(input);

            verify(readService).teacherTeachingCalendarWorkspace(
                    "tenant-a", "custom-user-id", "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of("school-1"), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", "custom-user-id",
                            List.of("lms.schedule.read_self"),
                            List.of()));
            assert result.equals(expected);
        }

        @Test
        void rejectsMissingPermission() {
            authenticate(principal("tenant-a", "teacher-1",
                    List.of("some.other.permission"),
                    List.of()));

            assertThatThrownBy(() -> controller.myTeachingCalendar(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsDeniedPermission() {
            authenticate(principal("tenant-a", "teacher-1",
                    List.of("lms.schedule.read_self", "lms.schedule.read",
                            "lms.schedule.manage", "lms.manage", "*"),
                    List.of("*")));

            assertThatThrownBy(() -> controller.myTeachingCalendar(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsDeniedSpecificPermission() {
            authenticate(principal("tenant-a", "teacher-1",
                    List.of("lms.schedule.read_self", "lms.schedule.manage"),
                    List.of("lms.schedule.read_self")));

            assertThatThrownBy(() -> controller.myTeachingCalendar(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsUnauthenticatedAccess() {
            assertThatThrownBy(() -> controller.myTeachingCalendar(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsPrincipalWithoutUserId() {
            authenticate(principal("tenant-a", null,
                    List.of("lms.schedule.read_self"),
                    List.of()));

            assertThatThrownBy(() -> controller.myTeachingCalendar(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsPrincipalWithBlankUserId() {
            authenticate(principal("tenant-a", "",
                    List.of("lms.schedule.read_self"),
                    List.of()));

            assertThatThrownBy(() -> controller.myTeachingCalendar(input))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void rejectsTenantSpoofing() {
            authenticate(principal("tenant-a", "teacher-1",
                    List.of("lms.schedule.read_self"),
                    List.of()));
            MyCalendarRequest otherTenantInput = new MyCalendarRequest(
                    "tenant-b", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of("school-1"), List.of(), List.of(), List.of(), List.of());

            assertThatThrownBy(() -> controller.myTeachingCalendar(otherTenantInput))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(readService);
        }

        @Test
        void usesPrincipalTenantIdWhenRequestTenantIdIsNull() {
            authenticate(principal("tenant-a", "teacher-1",
                    List.of("lms.schedule.read_self"),
                    List.of()));
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "tenant-a", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of(), null, List.of(), null, null, "now");
            when(readService.teacherTeachingCalendarWorkspace(
                    "tenant-a", "teacher-1", "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of("school-1"), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", "teacher-1",
                            List.of("lms.schedule.read_self"),
                            List.of())))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.myTeachingCalendar(input);

            verify(readService).teacherTeachingCalendarWorkspace(
                    "tenant-a", "teacher-1", "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of("school-1"), List.of(), List.of(), List.of(), List.of(),
                    principal("tenant-a", "teacher-1",
                            List.of("lms.schedule.read_self"),
                            List.of()));
            assert result.equals(expected);
        }

        @Test
        void wildcardPrincipalSucceeds() {
            authenticate(ErgPrincipal.systemAdmin());
            MyCalendarRequest noTenantInput = new MyCalendarRequest(
                    null, "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of("school-1"), List.of(), List.of(), List.of(), List.of());
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "erg", "2026-01-01", "2026-01-31", "SCHOOL",
                    List.of(), null, List.of(), null, null, "now");
            when(readService.teacherTeachingCalendarWorkspace(
                    "erg", "system", "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of("school-1"), List.of(), List.of(), List.of(), List.of(),
                    ErgPrincipal.systemAdmin()))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.myTeachingCalendar(noTenantInput);

            verify(readService).teacherTeachingCalendarWorkspace(
                    "erg", "system", "2026-01-01", "2026-01-31",
                    "SCHOOL", List.of("school-1"), List.of(), List.of(), List.of(), List.of(),
                    ErgPrincipal.systemAdmin());
            assert result.equals(expected);
        }

        @Test
        void handlesNullInputGracefully() {
            authenticate(principal("tenant-a", "teacher-1",
                    List.of("lms.schedule.read_self"),
                    List.of()));
            CalendarWorkspaceResponse expected = new CalendarWorkspaceResponse(
                    "tenant-a", null, null, null,
                    List.of(), null, List.of(), null, null, "now");
            when(readService.teacherTeachingCalendarWorkspace(
                    "tenant-a", "teacher-1", null, null, null,
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(),
                    principal("tenant-a", "teacher-1",
                            List.of("lms.schedule.read_self"),
                            List.of())))
                    .thenReturn(expected);

            CalendarWorkspaceResponse result = controller.myTeachingCalendar(null);

            verify(readService).teacherTeachingCalendarWorkspace(
                    "tenant-a", "teacher-1", null, null, null,
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(),
                    principal("tenant-a", "teacher-1",
                            List.of("lms.schedule.read_self"),
                            List.of()));
            assert result.equals(expected);
        }

        @Test
        void rejectsNullInputWithoutAuthentication() {
            assertThatThrownBy(() -> controller.myTeachingCalendar(null))
                    .isInstanceOf(ForbiddenException.class);
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
            String userId,
            List<String> permissions,
            List<String> deniedPermissions
    ) {
        return new ErgPrincipal(
                userId,
                "session-1",
                tenantId,
                List.of("teacher"),
                permissions,
                deniedPermissions,
                List.of("lms"),
                "teacher",
                "school",
                Map.of()
        );
    }
}
