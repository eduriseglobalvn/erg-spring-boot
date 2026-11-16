package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lms.internal.academicrecord.LmsAcademicRecordGraphQlController;
import com.erg.ergspringboot.lms.internal.academicrecord.LmsAcademicRecordGraphQlController.GradebookWorkspaceInput;
import com.erg.ergspringboot.lms.internal.academicrecord.LmsAcademicRecordGraphQlController.UpsertAttendanceInput;
import com.erg.ergspringboot.lms.internal.academicrecord.LmsAcademicRecordUseCase;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AcademicRecordClassScopeTest {
    private final LmsAcademicRecordOperations records = mock(LmsAcademicRecordOperations.class);
    private final LmsAcademicRecordGraphQlController controller =
            new LmsAcademicRecordGraphQlController(records, new GraphQlAccessSupport(new ErgPolicyService()));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsClassOfAnotherSchool() {
        teacherOfSchoolA();
        when(records.schoolOfClass("tenant-a", "class-b")).thenReturn("school-B");

        // The client claims school-A, the school the teacher is allowed in; the class really belongs to school-B.
        assertThatThrownBy(() -> controller.gradebookWorkspace(
                new GradebookWorkspaceInput("tenant-a", "class-b", "school-A", "subject-1")))
                .isInstanceOf(ForbiddenException.class);
        verify(records, never()).gradebookWorkspace(any(), any(), any());
    }

    @Test
    void acceptsClassOfTheTeachersSchoolWhateverSchoolIdTheClientSends() {
        teacherOfSchoolA();
        when(records.schoolOfClass("tenant-a", "class-a")).thenReturn("school-A");

        controller.gradebookWorkspace(new GradebookWorkspaceInput("tenant-a", "class-a", "school-B", "subject-1"));

        verify(records).gradebookWorkspace("tenant-a", "class-a", "subject-1");
    }

    @Test
    void upsertAttendanceRejectsStudentNotInClass() {
        teacherOfSchoolA();
        when(records.schoolOfClass("tenant-a", "class-a")).thenReturn("school-A");
        doThrow(new ForbiddenException("Student does not belong to this class."))
                .when(records).assertStudentInClass("tenant-a", "class-a", "stranger-1");

        assertThatThrownBy(() -> controller.upsertAttendance(new UpsertAttendanceInput(
                "tenant-a", "class-a", "school-A", "stranger-1", "subject-1", "2026-11-16", 1, "PRESENT", 0)))
                .isInstanceOf(ForbiddenException.class);
        verify(records, never()).upsertAttendance(any());
    }

    @Test
    void useCaseRejectsStudentWhoIsNeitherInTheClassNorEnrolled() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(false);

        assertThatThrownBy(() -> new LmsAcademicRecordUseCase(jdbc)
                .assertStudentInClass("tenant-a", "class-a", "stranger-1"))
                .isInstanceOf(ForbiddenException.class);
    }

    private void teacherOfSchoolA() {
        ErgPrincipal teacher = new ErgPrincipal("teacher-1", "session-1", "tenant-a", List.of("teacher"),
                List.of("lms.attendance.read", "lms.attendance.write", "lms.grade.read", "lms.grade.write"),
                List.of(), List.of("lms"), "teacher", "managed",
                Map.of("managedEducationUnitIds", List.of("school-A")));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(teacher, null, List.of()));
    }
}
