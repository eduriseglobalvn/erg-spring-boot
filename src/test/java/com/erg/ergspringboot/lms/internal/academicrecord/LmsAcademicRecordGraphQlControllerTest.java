package com.erg.ergspringboot.lms.internal.academicrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LmsAcademicRecordResponses.AttendanceRecordResponse;
import com.erg.ergspringboot.lms.LmsAcademicRecordResponses.AttendanceWorkspaceResponse;
import com.erg.ergspringboot.lms.internal.academicrecord.LmsAcademicRecordGraphQlController.AttendanceWorkspaceInput;
import com.erg.ergspringboot.lms.internal.academicrecord.LmsAcademicRecordGraphQlController.UpsertAttendanceInput;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LmsAcademicRecordGraphQlControllerTest {
    @Mock private LmsAcademicRecordOperations service;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void attendanceReadDerivesTenantAndChecksClassScope() {
        authenticate(principal("teacher-1", "tenant-a", List.of("class-1")));
        AttendanceWorkspaceResponse expected = new AttendanceWorkspaceResponse(
                "tenant-a", "class-1", "subject-1", "2026-07-01", "2026-07-30", List.of());
        when(service.attendanceWorkspace(any(), any(), any(), any(), any())).thenReturn(expected);
        assertThat(controller().attendanceWorkspace(new AttendanceWorkspaceInput(
                "tenant-a", "class-1", null, "subject-1", "2026-07-01", "2026-07-30"))).isSameAs(expected);
        verify(service).attendanceWorkspace("tenant-a", "class-1", "subject-1", "2026-07-01", "2026-07-30");
    }

    @Test
    void attendanceWriteUsesAuthenticatedActor() {
        authenticate(principal("teacher-1", "tenant-a", List.of("class-1")));
        AttendanceRecordResponse response = new AttendanceRecordResponse(
                "record-1", "student-1", "subject-1", "2026-07-30", 1, "PRESENT", 1, OffsetDateTime.now());
        when(service.upsertAttendance(any())).thenReturn(response);
        controller().upsertAttendance(new UpsertAttendanceInput(
                null, "class-1", null, "student-1", "subject-1", "2026-07-30", 1, "PRESENT", 0));
        ArgumentCaptor<LmsAcademicRecordOperations.AttendanceCommand> captor =
                ArgumentCaptor.forClass(LmsAcademicRecordOperations.AttendanceCommand.class);
        verify(service).upsertAttendance(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(captor.getValue().actorUserId()).isEqualTo("teacher-1");
    }

    @Test
    void rejectsCrossTenantBeforeService() {
        authenticate(principal("teacher-1", "tenant-a", List.of("class-1")));
        assertThatThrownBy(() -> controller().attendanceWorkspace(new AttendanceWorkspaceInput(
                "tenant-b", "class-1", null, "subject-1", "2026-07-01", "2026-07-30")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(service);
    }

    private LmsAcademicRecordGraphQlController controller() {
        return new LmsAcademicRecordGraphQlController(service, new GraphQlAccessSupport(new ErgPolicyService()));
    }

    private void authenticate(ErgPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private ErgPrincipal principal(String userId, String tenantId, List<String> classIds) {
        return new ErgPrincipal(userId, "session-1", tenantId, List.of("teacher"),
                List.of("lms.attendance.read", "lms.attendance.write", "lms.grade.read", "lms.grade.write"),
                List.of(), List.of("lms"), "teacher", "managed", Map.of("managedClassIds", classIds));
    }
}
